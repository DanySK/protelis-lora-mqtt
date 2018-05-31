function Decode(fPort, bytes) {
  return {
    "transaction": bytes[bytes.length - 2],
    "frame": bytes[bytes.length - 1],
    "payload": bytes.slice(0, bytes.length - 2).map(function (x) { return parseInt(x.toString()) } )
	};
}

function Encode(fPort, obj) {
  return obj.payload.concat([obj.transaction, obj.frame]);
}

var a = [0, 1, 2, 3, 234, 255]
console.log(a)
var dec = Decode(1, a)
console.log(dec)
var enc = Encode(1, dec)
console.log(enc)
var redec = Decode(1, enc)
console.log(redec)