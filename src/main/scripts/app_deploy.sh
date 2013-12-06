#command line arguments...
OPTIND=1   

do_link=0
do_start=0
do_chkconf=0
verbose=0
while getopts "h?vlsc" opt; do
    case "$opt" in
    l)  do_link=1
        ;;
    s)  do_start=1
    	  do_link=1
        ;;
    c)  do_chkconf=1
    	  do_link=1
        ;;
    v)  verbose=1
        ;;
    esac
done

shift $((OPTIND-1))

[ "$1" = "--" ] && shift

currentDir="$1"
module="$2"
version="$3"

pushd $currentDir > /dev/null

echo "Configuring application..."
./gradlew createConfigSkeleton 

if [ $? -ne 0 ]
then
        echo "Configuration failed, probably some config values are missing, see log for details"
        echo "Any missing properties reported in file 'missing-properties-<host>.txt' in uses home dir"
        cp missing* ~/.
        exit 5
else
        echo "Configuration OK!"
fi


install_base_dir="/opt/netgiro"
install_dir="${install_base_dir}/$module-$version"
install_link="${install_base_dir}/$module"
old_install=""


if [ -h $install_link ]
then
        old_install=`readlink -f $install_link`
        if [ $do_start = 1 ]
        then
          echo "Stopping running applicaion!"
          $install_link/scripts/server.sh stop
        fi
        if [ $do_link = 1 ]
        then
	        echo "Removing old link"
	        rm $install_link
	      fi
fi

if [ -d $install_dir ]
then
  echo "Removing old identical version"
  rm -rf $install_dir
fi

mkdir $install_dir
if [ $? -ne 0 ]
then
  echo "Unable to create install dir $install_dir, terminating"
  popd
  exit 4
fi

cp -r lib $install_dir/
cp -r scripts $install_dir/
cp -r settings $install_dir/

popd > /dev/null
pushd $install_base_dir > /dev/null

if [ $do_link = 1 ]
then
  echo "Creating link ${module} -> ${module}-${version}"
  ln -s ${module}-${version} $module
fi

echo "Setting premissions on script and correcting ownerships"
chmod 755 $install_dir/scripts/server.sh
chown -R netgiro:netgiro ${install_dir}

std_out_file="/var/opt/netgiro/$module.stdout.log"
echo "cleaning logfile $std_out_file"
date > $std_out_file
chown -R netgiro:netgiro ${std_out_file}

echo "Creating diffs..."
diff -rbB $old_install/settings $install_dir/settings > $install_dir/diff_settings
diff -rbB $old_install/scripts $install_dir/scripts > $install_dir/diff_scripts

if [ $do_chkconf = 1 ]
then
  pushd /etc/init.d > /dev/null
  if [ ! -h $module ]
		then
        echo "Adding module to chkconfig and init.d"
        ln -s /opt/netgiro/$module/scripts/server.sh $module
        chkconfig --add ${module}
		fi

  popd > /dev/null
fi

if [ $do_start = 1 ] 
then
	echo "Starting app: $module $version"
  $install_link/scripts/server.sh restart
elif [ -h $module ]
then 
  echo "Application status:"
  $install_link/scripts/server.sh status
else 
 	exit 0
fi
