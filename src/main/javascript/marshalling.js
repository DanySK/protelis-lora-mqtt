function Decode(fPort, bytes) {
  return {
    "transaction": bytes[0],
    "framecount": bytes[1],
    "frame": bytes[2],
    "payload": bytes.slice(0, bytes.length - 2).map(function (x) { return parseInt(x.toString()) } )
	};
}

function Encode(fPort, obj) {
  return [obj.transaction, obj.framecount, obj.frame].concat(obj.payload);
}

var a = [0, 1, 2, 3, 234, 255]
console.log(a)
var dec = Decode(1, a)
console.log(dec)
var enc = Encode(1, dec)
console.log(enc)
var redec = Decode(1, enc)
console.log(redec)