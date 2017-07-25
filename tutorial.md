#Sharemind Web Application Tutorial

[TOC]

##Introduction
Sharemind Web Gateway is a Node.js server that allows web applications to utilize Sharemind's privacy preserving features. Each Sharemind server hosts one instance of the Web Gateway which relays data and instructions between the web client and Sharemind host.
This tutorial aims to teach how to create Sharemind web applications through a simple example application. The example receives a clients location data with the HTML5 Navigator API, secret shares it and calculates a histogram of distances to other clients.

##Setup
To run the example application, you will need the Sharemind servers, Sharemind Web Client and Web Gateway APIs, Node.js and NPM.
In the `package.json` file specify the location of the Web Client and Web Gateway APIs in your file system. Run `npm install` to install the necessary dependencies and copy them to the correct location.
In the folder `gateway/nodejs/` make sure you have the correct keys and the config files are set up correctly for your system. Run `sh run_gateway.sh` to start the gateways. Open `webserver/index.html` in your browser.

##Application Walkthrough
To make the tutorial easier to follow the example application is as bare bones as possible, containing no style sheets or animations. In addition, the code prioritizes readability over speed or precision.

###Gateway
The gateway is a Node.js application that runs on top of each Sharemind server. It can be found in the folder `gateway/nodejs`. A shell script runs all three instances of the gateway with the right configurations. Note this only works if all Sharemind instances are run from the same computer.
The gateways require each Sharemind server's public keys and the controller's private and public keys.
For most use cases `gateway.js` should only be edited to add runnable SecreC scripts. The variable `scriptsInfoMap` contains an object for each script that can be called by the web client.

###Web Client
The Web Client, in this case, is a single HTML file `webserver/index.html` that contains the web page and JavaScript.
The HTML part of this file contains a single button that executes the function `getLocation()` and a div that will contain the returned histogram.
In the head of the HTML file, three external javascript modules are included: socket.io, jsbn and sharemind-web-client. The location of these modules is specified in `package.json`.
The embedded JavaScipt contains functions to get location data and send it to the servers and variables to support those actions. The list `hosts` contains a string of the IP address and port for each gateway. Because this example application is meant to be run on the same device as the gateways, the IP addresses are `localhost`. The variables `pub` and `priv` are Sharemind protection domains that are used to create variables of those domains.
In sharemind, all data are stored as arrays, behind the scenes scalar values are just arrays with a single element. Because of this, the Sharemind-Web-Client only allows the creation of arrays. The available data types are:

* IntNArray
* UintNArray
* XorUintNArray
* Float32Array
* Float64Array

where N is 8, 16, 32 or 64. Variable length strings are only available as public variables, for private bound length strings XorUint8Array is used internally with a public Uint64 that specifies the string bound.
```javascript
var public_value = new pub.Int64Array(3);  //creates a public array with 3 elements
public_value.set(0, 900);  //set the first element to 900

var private_value = new priv.Int64Array(public_value)  //secret share the public array
```
A new gateway connection object is created with `new sm.client.GatewayConnection(hosts)`, besides a list of gateway locations, Socket.io options and a callback function can be given as optional arguments. The connection is opened with `gatewayConnection.openConnection(callback(error, serverInfos, prngSeed));`. A callback function is called once the connection is established. In the callback function errors are logged, if a pseudorandom number generator doesn't exist, it is created, public and private variables are declared and a SecreC script is run. The function `gatewayConnection.runMpcComputation(script-name, arguments, callback(error, results))` runs the specified script on the Sharemind servers with the given arguments and calls the callback function once the script has finished. The script must be declared in `scriptsInfoMap` in `gateway.js`.

###SecreC Script
SecreC is a domain specific language for Sharemind that is designed to look like C. The main difference between SecreC and C is that in SecreC every variable has a protection domain that can be either public or private. Variables that are private are secret shared and computations on those variables are done on the Sharemind servers.
