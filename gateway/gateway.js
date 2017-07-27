/*
 * This file is a part of the Sharemind framework.
 * Copyright (C) Cybernetica AS
 *
 * All rights are reserved. Reproduction in whole or part is prohibited
 * without the written consent of the copyright owner. The usage of this
 * code is subject to the appropriate license agreement.
 */

/* eslint-env node */

// Parse command line arguments
// usage: 'node gateway.js <name> <conf> <port> <hostname>'
if (process.argv.length <= 2)
  throw new Error('Usage: node gateway.js gatewayNumber [port] [hostname] [confFile]');

var gatewayNumber = parseInt(process.argv[2]);
if (isNaN(gatewayNumber) || gatewayNumber < 1 || gatewayNumber > 4)
  throw new Error('Invalid gatewayNumber argument, expecting an integer between 1 and 3 inclusive');

var gatewayName = 'Gateway' + gatewayNumber;
var gatewayPort = process.argv.length > 3 ? parseInt(process.argv[3]) : 8080 + gatewayNumber;
var gatewayHostname = process.argv.length > 4 ? process.argv[4] : 'localhost';
var gatewayConfig = process.argv.length > 5 ? process.argv[5] : 'gateway' + gatewayNumber + '.cfg';

var logPrefix = '[' + gatewayName + ']';

function assert(cond, msg) {
  if (!msg)
    msg = "";
  console.assert(cond, msg);
}

function log(msg) {
  console.log(logPrefix + "[INFO]  " + msg);
}

function debug(msg) {
  console.log(logPrefix + "[DEBUG] " + msg);
}

function logErr(err, msg) {
  assert(err);
  if (msg)
    console.error(logPrefix + "[ERROR] " + msg);
  else
    console.error(logPrefix + "[ERROR] An error occurred:");
  console.error(err.stack || err);
}

// Main WebGateway object
var gateway;
// Http server object
var server;

// Gracefully close HTTP server
function closeHttpServer(callback) {
  if (server && server.address()) {
    log("Closing HTTP server...");
    server.close(function () {
      log("Closed listening server.");
      callback();
    });
  } else {
    callback();
  }
}

// Gracefully close the WebGateway object
function closeWebGateway(callback) {
  if (gateway) {
    log("Closing gateway...");
    gateway.close(function(err) {
      if (err) {
        logErr(err, "Error when closing WebGateway object:");
      }
      log("Disconnected from Sharemind and closed WebGateway object.");
      callback();
    });
  } else {
    callback();
  }
}

function stop(rv) {
  closeHttpServer(function() {
    closeWebGateway(function() {
      process.exit(rv);
    });
  });
}

process.on("SIGTERM", function () {
  log("Caught SIGTERM, closing gateway...");
  stop(0);
});

process.on("SIGINT", function () {
  log("Caught SIGINT, closing gateway...");
  stop(0);
});

process.on('uncaughtException', function(err) {
  logErr(err, "Uncaught exception:");
  stop(1);
});

// Set up HTTP server
function requestHandler(req, res) {
  res.writeHead(200);
  res.end('');
}

var http = require('http');
server = http.createServer(requestHandler);

// Example of setting up HTTPS instead of HTTP
/*
var http  = require('https');
var fs    = require('fs');

// Read in certificate and key
var options = {
 key:   fs.readFileSync('server.key'),
 cert:  fs.readFileSync('cert-server.pem'),
// ca:  fs.readFileSync('chain-server.pem')
};

var server = http.createServer(options, requestHandler);
*/

// The main Sharemind Web Application Gateway API
var WebGateway = require('sharemind-web-gateway');
// Socket.io is used to communicate with web browser clients
var io = require('socket.io')(server);

// All Gateways must know the server names of all Sharemind servers
// A Sharemind server's name is set int the server's configuration file
var otherServerNames = [
  'DebugMiner1',
  'DebugMiner2',
  'DebugMiner3'];
otherServerNames.splice(gatewayNumber-1, 1);

// Specify scripts that clients can run
// Client requests running computation 'location-database', upon which the script 'location-database.sb' is run
var scriptsInfoMap = {};
scriptsInfoMap['location-database'] = {
  name: 'location-database.sb',
  type: 'multi', // 'multi' means this script does MPC computations
  otherServerNames: otherServerNames
};

// Define how new client connections are handled
io.on('connection', function(client) {
  log(' New client \'' + client.id + '\' connected from: ' + client.request.connection.remoteAddress +
      ':' + client.request.connection.remotePort);
  var clientID = client.id;

  // This initializes all logic to serve client requests for running computations on Sharemind
  // Specific event listeners are registered on the socket object, which are documented in 'sharemind-web-gateway' module
  var clientEvents = gateway.handleNewClient(client, scriptsInfoMap, {
    startMpcProcessTimeout: 10000
  });

  // Can inject application-specific logic to handling of client requests in addition to the main flow,
  // possible events are documented in 'sharemind-web-gateway' module.
  // 'log', 'logErr' and 'debug' events are used to send log and debug messages to the application
  clientEvents
    .on('log', function(msg) {
      log('[' + clientID + '] ' + msg);
    })
    .on('logErr', function(err, msg) {
      logErr(err, '[' + clientID + '] ' + msg);
    })
    .on('debug' ,function(msg) {
      debug('[' + clientID + '] ' + msg);
    });

  // Can specify custom app-specific events also
  client.on('anything', function (data) {
    debug('anything event');
    if (data) {
      debug(JSON.stringify(data));
    }
  });
});

log('Initializing WebGateway...');
// Initialize the WebGateway object, read in configuration
gateway = WebGateway.getInstance(gatewayConfig, log);

log('WebGateway initialized. connecting to Sharemind server with server info: ' + JSON.stringify(gateway.getServerInfo()));

// Connect gateway to Sharemind and start the HTTP server
gateway.connect(function (err) {
  if (err) {
    logErr(err, 'Error when connecting to Sharemind');
    return stop(1);
  }

  log('Connected gateway to Sharemind. Running HTTP server...');
  server.listen(gatewayPort, gatewayHostname);
  log('Gateway listening on hostname: \'' + gatewayHostname + '\', port: \'' + gatewayPort + '\'');
});
