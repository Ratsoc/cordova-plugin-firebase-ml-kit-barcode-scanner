#!/usr/bin/env node
"use strict";

const fs = require("fs");

var config = fs.readFileSync("config.xml").toString();
var name = config.match(/<name>([^<]*)<\/name>/)[1];

const IOSPlatform = "platforms/ios";
const IOSDestination = IOSPlatform + "/" + name + "/Resources/GoogleService-Info.plist";
const IOSSrc = "GoogleService-Info.plist";

module.exports = function(context) {
  for(let platform of context.opts.platforms) {
    if (platform == "ios") {
      var plist = fs.readFileSync(IOSSrc).toString();
      fs.writeFileSync(IOSDestination, plist);
    }
  }
}
