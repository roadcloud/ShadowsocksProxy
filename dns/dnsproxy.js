/**
 * Created with JetBrains WebStorm.
 * User: YongHua
 * Date: 12-12-10
 * Time: 下午11:19
 * To change this template use File | Settings | File Templates.
 */
var http = require('http');
var url = require('url');
var dns = require('dns');

http.createServer(function (req, res) {
    var urlParts = url.parse(req.url, true);
    var query = urlParts.query;
    var encHost = query['d'];

    res.writeHead(200, {'Content-Type':'text/plain'});

    if (encHost != null) {
        var host = new Buffer(new Buffer(encHost, 'base64').toString('ascii'), 'base64').toString('ascii');
        dns.lookup(host, function (err, addresses) {
            if (err) throw err;

            console.log(host + ":" + addresses);
            res.end(addresses);
        })
    }
}).listen(8000, function () {
        console.log('Server running at 8000');
    });