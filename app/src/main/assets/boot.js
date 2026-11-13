/*
 * 注入引导脚本（纯 ASCII，由 Java 直接 evaluateJavascript 执行）。
 * enhance.js / enhance.css 以 UTF-8 Base64 传入，彻底规避 GBK 页面、
 * 引号转义、换行符和中文字符带来的编码问题。
 */
window.R52BOOT = function (jsB64, cssB64, opts) {
  function dec(b64) {
    var bin = atob(b64);
    var arr = new Uint8Array(bin.length);
    for (var i = 0; i < bin.length; i++) arr[i] = bin.charCodeAt(i);
    if (typeof TextDecoder !== 'undefined') {
      return new TextDecoder('utf-8').decode(arr);
    }
    var raw = '';
    for (var j = 0; j < arr.length; j++) raw += String.fromCharCode(arr[j]);
    return decodeURIComponent(escape(raw));
  }
  window.__R52_DEC__ = dec;
  if (!window.R52 || !window.R52.__v) {
    window.eval(dec(jsB64));
  }
  window.R52.install(cssB64, opts);
};
