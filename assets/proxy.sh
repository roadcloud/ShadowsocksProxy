#!/system/bin/sh

DIR=/data/data/org.shadowsocks

PATH=$DIR:$PATH

case $1 in
 start)

echo "
base {
 log_debug = off;
 log_info = off;
 log = stderr;
 daemon = on;
 redirector = iptables;
}

redsocks {
 local_ip = 127.0.0.1;
 local_port = 8123;
 ip = 127.0.0.1;
 port = 8390;
 type = socks5;
} 

" > $DIR/redsocks.conf

  $DIR/redsocks -p $DIR/redsocks.pid -c $DIR/redsocks.conf
  
echo -n "
{
    \"server\":\"$2\",
    \"server_port\":$4,
    \"local_port\":8390,
    \"password\":\"$3\",
    \"timeout\":60
}
" > $DIR/config.json
  
  $DIR/python-cl $DIR/local.py

  ;;
stop)
  kill -9 `cat $DIR/redsocks.pid`
  kill -9 `cat $DIR/python.pid`
  rm -f $DIR/redsocks.conf
  rm -f $DIR/redsocks.pid
  rm -f $DIR/python.pid
  
  killall -9 python-cl
  killall -9 redsocks
  $DIR/iptables -t nat -F OUTPUT
  
  ;;
esac
