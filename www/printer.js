	var BluetoothPrinterLoader = function (require, exports, module) {
		var exec = require("cordova/exec");
        function BluetoothPrinter() {
        };

        BluetoothPrinter.prototype.list = function (successCallback, errorCallback) {
            exec(successCallback, errorCallback, 'BluetoothPrinter', 'list', []);
        };
	   	BluetoothPrinter.prototype.open = function(fnSuccess, fnError, name) {
	    	exec(fnSuccess, fnError, "BluetoothPrinter", "open", [name]);
	   	};
		BluetoothPrinter.prototype.close = function(fnSuccess, fnError){
			exec(fnSuccess, fnError, "BluetoothPrinter", "close", []);
   		};
		BluetoothPrinter.prototype.print = function(fnSuccess, fnError, str){
			exec(fnSuccess, fnError, "BluetoothPrinter", "print", [str]);
   		};
		BluetoothPrinter.prototype.printImage = function(fnSuccess, fnError, str){
			exec(fnSuccess, fnError, "BluetoothPrinter", "printImage", [str]);
   		};
        
		var BluetoothPrinter = new BluetoothPrinter();
        module.exports = BluetoothPrinter;

    }

    BluetoothPrinterLoader(require, exports, module);

    cordova.define("cordova/plugin/BluetoothPrinter", BluetoothPrinterLoader);


/*
var exec = require('cordova/exec');

var printer = {
   },
};

module.exports = printer;
*/
