# chkconfig: 3 99 1
# description: FIXME
# $Revision: 1.5 $

system=`uname`

user=`/usr/bin/id|cut -d"=" -f2 | cut -d"(" -f2 | cut -d")" -f1`

########### S E T   T H E S E  ############
APP_NAME=FIXME
APP_CLASS=FIXME
JVM_ARGS='-server -Xmx128M -Xms64M -XX:+UseConcMarkSweepGC -XX:+UseParNewGC -da '
########### S E T   T H E S E   E N D############


############# A P P L I C A T I O N  P A R A M S #######################
APP_ROOT=/opt/netgiro/$APP_NAME
APP_LIB=$APP_ROOT/lib
APP_JAR=$APP_LIB/$APP_NAME.jar
APP_CFG=$APP_ROOT/settings
STDOUT_LOG_FILE=/var/opt/netgiro/$APP_NAME.stdout.log
PID_FILE=/var/opt/netgiro/pid/$APP_NAME.pid

ALL_JARS=$APP_JAR:$APP_LIB/*
JAVA_HOME=/usr/java/java6
############# E N D  A P P L I C A T I O N  P A R A M S #######################


# FIXME, if dont use db, remove this section
COMMON=/opt/netgiro/common
###########    D B   D R I V E R S  ###########
# Would like to be sure that we're not using the Win32 zip file so
# in this section we should delete the file and create the link 
# after deletion.
    rm -f $APP_LIB/db2jcc.jar
    rm -f $APP_LIB/db2jcc_license_cu.jar
    /bin/ln -s $COMMON/db2/db2jcc.jar $APP_LIB/db2jcc.jar
    /bin/ln -s $COMMON/db2/db2jcc_license_cu.jar $APP_LIB/db2jcc_license_cu.jar
###########    E N D  D B   D R I V E R S  ###########


# FIXME must gurantee that we ALWAYS run as netgiro
if (test $user = "root") then
 echo "Root user detected, changing to netgiro"
 su - netgiro -c "$APP_ROOT/scripts/server.sh $*"
 exit;
fi

if ( test $user != "netgiro") then
 echo "Must be user root or netgiro!"
 exit -1;
fi

TERM=vt100;export TERM
LANG=en_US.UTF-8;export LANG
umask 022

###########   U N I X - L I N U X  P A R A M E T E R S    ###########

case $system in
  "Linux")
    echo "System is Linux"
    LC_CTYPE=sv_SE;export LC_CTYPE
    ;;
  "SunOS")
    echo "System is SunOS"
    ;;
  *)
    echo "Start script cannot handle the current OS identified as: $system"
    exit -2
    ;;
esac

###########   E N D   U N I X - L I N U X  P A R A M E T E R S    ###########

START_CMD="$JAVA_HOME/bin/java $JVM_ARGS -classpath $APP_CFG:$APP_RES:$APP_JAR:$ALL_JARS $APP_CLASS $APP_ARGS"

start() {
        if [ -f $PID_FILE ];
        then
					echo "$PID_FILE exists, checking if $APP_NAME is already running?"
          PID_TO_KILL=`cat $PID_FILE`
          if ps -p $PID_TO_KILL > /dev/null
					then
   					echo "Application $APP_NAME (pid: $PID) is running, leaving it alone"
						exit 1				
					else 
						echo "Removing dead pid file $PID_FILE"
						rm $PID_FILE
					fi
        fi
        pushd $APP_ROOT >> /dev/null
        touch $STDOUT_LOG_FILE
        echo "`/bin/date`: Start issued from SSH_CLIENT: $SSH_CLIENT by $LOGNAME" >> $STDOUT_LOG_FILE
        echo "Run as OS user: " $user >> $STDOUT_LOG_FILE
        echo "Starting $APP_NAME with $START_CMD" >> $STDOUT_LOG_FILE
        $START_CMD >> $STDOUT_LOG_FILE 2>&1 &
        PID=$!
        echo "Application $APP_NAME started with pid $PID stored in pidfile $PID_FILE"
        echo "Application $APP_NAME started with pid $PID stored in pidfile $PID_FILE" >> $STDOUT_LOG_FILE
        echo "Startup info:"
        echo "tail -40f $STDOUT_LOG_FILE"
        echo $PID > $PID_FILE
        popd >> /dev/null
}

stop () {
        if [ ! -f $PID_FILE ];
        then
            echo "$PID_FILE does not exists, assuming $APP_NAME NOT started"
        else
          PID_TO_KILL=`cat $PID_FILE`
          echo "Stopping $APP_NAME with pid $PID_TO_KILL"
          echo "`/bin/date`: Stop issued from SSH_CLIENT: $SSH_CLIENT by $LOGNAME" >> $STDOUT_LOG_FILE
          echo "Run as OS user: " $user >> $STDOUT_LOG_FILE
          echo "kill -TERM $PID_TO_KILL"
          kill -TERM $PID_TO_KILL
          if [ $? -ne 0 ]
          then
            echo "Failed to stop $APP_NAME, it's probably stopped but pid file: $PID_FILE exists -> remove the pid file"
            exit 0
          fi
          echo "Checking pid $PID_TO_KILL"
          EXIT_STATUS=3
          for i in {1..10} #wait max 10 seconds
          do
            sleep 1
            if ps -p $PID_TO_KILL > /dev/null
            then  
              echo "Still running..."
            else
                echo "Appplication $APP_NAME stopped ok"
                EXIT_STATUS=0
                break
            fi
          done

          if [ 0 -eq $EXIT_STATUS ]
          then
                echo "removing pid-file: $PID_FILE ..."
                rm $PID_FILE
          else
            echo "$APP_NAME did not terminate within time (10 seconds), help needed. Invetigate what went wrong and manually kill the process with pid $PID_TO_KILL"
            exit $EXIT_STATUS
          fi
        fi
}

check() {
        if [ ! -f $PID_FILE ];
        then
        	echo "$APP_NAME not started, $PID_FILE does not exist"
          exit 0;
        fi
        PID_TO_KILL=`cat $PID_FILE`
        echo "Checking $APP_NAME running with pid=$PID_TO_KILL?"
        kill -0 $PID_TO_KILL >> /dev/null 2>&1
        if [ $? -ne 0 ]
        then
        	echo "Appplication $APP_NAME has stopped!"
          exit 0;
        fi
        echo "$APP_NAME running!"
        exit 0;
}

case "$1" in

'start')
        start
        ;;

'stop')
        stop
        ;;

'status')
        check
        ;;

'check')
        check
        ;;

'restart')
        stop
        start
        ;;

*)
        echo
    echo "Usage: " basename $0" { start | restart | stop | check | status }"
    echo
        ;;

esac
