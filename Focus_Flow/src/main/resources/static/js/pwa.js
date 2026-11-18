// Registers the service worker that makes FOCUS FLOW installable. Works on HTTPS and on localhost only.
if ("serviceWorker" in navigator) {
  window.addEventListener("load", () => {
    navigator.serviceWorker.register("/sw.js", { scope: "/" }).catch(() => {});
  });
}
