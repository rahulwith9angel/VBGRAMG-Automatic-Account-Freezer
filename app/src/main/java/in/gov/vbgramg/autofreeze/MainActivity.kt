package in.gov.vbgramg.autofreeze

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var tvStatus: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnReload: Button

    @Volatile
    private var isAutomationRunning = false

    private val portalUrl = "https://vbgramgde2.dord.gov.in/vbgramg/states/Freez_ac.aspx"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        tvStatus = findViewById(R.id.tvStatus)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        btnReload = findViewById(R.id.btnReload)

        setupWebView()

        btnReload.setOnClickListener {
            webView.loadUrl(portalUrl)
        }

        btnStart.setOnClickListener {
            isAutomationRunning = true
            btnStart.isEnabled = false
            btnStop.isEnabled = true
            updateStatus("Starting automation across Panchayats...")
            // Reset persisted Panchayat index for a fresh run
            webView.evaluateJavascript("localStorage.removeItem('vbgramg_freeze_2026_state');", null)
            injectAutomationScript()
        }

        btnStop.setOnClickListener {
            isAutomationRunning = false
            btnStart.isEnabled = true
            btnStop.isEnabled = false
            webView.evaluateJavascript(
                "window.STOP_FREEZE = true; localStorage.removeItem('vbgramg_freeze_2026_state');",
                null
            )
            updateStatus("Stopped by user.")
        }

        webView.loadUrl(portalUrl)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        // Desktop Chrome User-Agent so ASP.NET renders full desktop table layout
        settings.userAgentString =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.addJavascriptInterface(AndroidBridge(), "AndroidBridge")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onJsAlert(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                if (isAutomationRunning) {
                    updateStatus("Portal Alert: ${message ?: ""}")
                    result?.confirm()
                    return true
                }
                return super.onJsAlert(view, url, message, result)
            }

            override fun onJsConfirm(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                if (isAutomationRunning) {
                    result?.confirm()
                    return true
                }
                return super.onJsConfirm(view, url, message, result)
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                return super.onConsoleMessage(consoleMessage)
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (isAutomationRunning) {
                    updateStatus("Page reloading (ASP.NET postback)...")
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Re-inject automatically if a full-page postback occurred while running
                if (isAutomationRunning) {
                    webView.postDelayed({
                        if (isAutomationRunning) {
                            injectAutomationScript()
                        }
                    }, 900)
                }
            }
        }
    }

    private fun updateStatus(msg: String) {
        runOnUiThread {
            tvStatus.text = "Status: $msg"
        }
    }

    private fun injectAutomationScript() {
        val jsCode = """
            (async function () {
              if (window.__VBGRAMG_WORKER_ACTIVE) return;
              window.__VBGRAMG_WORKER_ACTIVE = true;

              const TARGET_YEAR = "2026";
              const STORAGE_KEY = "vbgramg_freeze_2026_state";
              const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
              const logStatus = (m) => {
                if (window.AndroidBridge && window.AndroidBridge.onStatus) {
                  window.AndroidBridge.onStatus(m);
                }
              };

              function getPanchayatSelect() {
                const selects = Array.from(document.querySelectorAll("select"));
                for (const sel of selects) {
                  const id = (sel.id || "").toLowerCase();
                  if (id.includes("panch")) return sel;
                }
                return selects[2] || null;
              }

              function getDataTable() {
                return Array.from(document.querySelectorAll("table")).find(
                  (t) => t.innerText.includes("JobCard No.") && t.innerText.includes("VerifiedDate")
                );
              }

              function getPageSignature() {
                const tbl = getDataTable();
                if (!tbl) return "EMPTY";
                const rows = Array.from(tbl.querySelectorAll("tr")).filter((r) =>
                  r.querySelector('input[type="checkbox"]')
                );
                if (!rows.length) return "NO_ROWS";
                return rows.map((r) => r.innerText.trim().slice(0, 60)).join("|");
              }

              async function waitForPostback(prevSignature, timeout = 25000) {
                const start = Date.now();
                await sleep(700);
                while (Date.now() - start < timeout) {
                  if (window.STOP_FREEZE) return false;
                  const prm = window.Sys?.WebForms?.PageRequestManager?.getInstance?.();
                  const inAsync = prm ? prm.get_isInAsyncPostBack() : false;
                  const curSig = getPageSignature();
                  if (!inAsync && curSig !== prevSignature && curSig !== "EMPTY") {
                    await sleep(500);
                    return true;
                  }
                  await sleep(350);
                }
                await sleep(500);
                return false;
              }

              function findButton(pattern) {
                const candidates = Array.from(
                  document.querySelectorAll('input[type="submit"], input[type="button"], button')
                );
                return candidates.find((el) =>
                  pattern.test((el.value || el.innerText || "").trim())
                );
              }

              function getNextPageLink() {
                const tbl = getDataTable();
                if (!tbl) return null;
                const allRows = Array.from(tbl.querySelectorAll("tr"));
                const pagerRow = allRows.reverse().find((r) => {
                  const links = r.querySelectorAll("a[href*='__doPostBack']");
                  const activeSpan = r.querySelector("span");
                  return links.length > 0 && activeSpan && /^\d+$/.test(activeSpan.innerText.trim());
                });
                if (!pagerRow) return null;

                const currentPage = parseInt(pagerRow.querySelector("span").innerText.trim(), 10);
                const links = Array.from(pagerRow.querySelectorAll("a"));
                const exactNext = links.find(
                  (a) => parseInt(a.innerText.trim(), 10) === currentPage + 1
                );
                if (exactNext) return exactNext;

                const cells = Array.from(pagerRow.querySelectorAll("td"));
                const curIdx = cells.findIndex((td) => td.querySelector("span"));
                if (curIdx !== -1) {
                  for (let i = curIdx + 1; i < cells.length; i++) {
                    const a = cells[i].querySelector("a");
                    if (a && a.innerText.trim() === "...") return a;
                  }
                }
                return null;
              }

              window.confirm = () => true;
              window.alert = (msg) => logStatus("Alert: " + msg);
              window.STOP_FREEZE = false;

              try {
                const panchSelect = getPanchayatSelect();
                if (!panchSelect) {
                  logStatus("Panchayat dropdown not found. Open Freez_ac.aspx & select Block.");
                  window.__VBGRAMG_WORKER_ACTIVE = false;
                  return;
                }

                const panchOptions = Array.from(panchSelect.options).filter(
                  (opt) => opt.value && opt.value !== "0" && !/select/i.test(opt.text)
                );

                let state = JSON.parse(localStorage.getItem(STORAGE_KEY) || "null");
                if (!state || !state.running) {
                  state = { running: true, panchIndex: 0 };
                  localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
                }

                for (let pIdx = state.panchIndex; pIdx < panchOptions.length; pIdx++) {
                  if (window.STOP_FREEZE) break;

                  state.panchIndex = pIdx;
                  localStorage.setItem(STORAGE_KEY, JSON.stringify(state));

                  const curSelect = getPanchayatSelect();
                  const opt = panchOptions[pIdx];
                  const pName = opt.text.trim();
                  logStatus("[" + (pIdx + 1) + "/" + panchOptions.length + "] Panchayat: " + pName);

                  if (curSelect && curSelect.value !== opt.value) {
                    const sigBefore = getPageSignature();
                    curSelect.value = opt.value;
                    curSelect.dispatchEvent(new Event("change", { bubbles: true }));
                    await waitForPostback(sigBefore);
                  }

                  const proceedBtn = findButton(/^proceed$/i);
                  if (proceedBtn) {
                    const sigBefore = getPageSignature();
                    proceedBtn.click();
                    await waitForPostback(sigBefore);
                  }

                  let pageNum = 1;
                  while (!window.STOP_FREEZE) {
                    const tbl = getDataTable();
                    if (!tbl) break;

                    const headerCells = Array.from(tbl.querySelectorAll("tr:first-child th, tr:first-child td"));
                    let verifiedDateColIdx = headerCells.findIndex((c) =>
                      /A\/c\s*VerifiedDate/i.test(c.innerText)
                    );
                    if (verifiedDateColIdx === -1) verifiedDateColIdx = 8;

                    while (!window.STOP_FREEZE) {
                      const curTable = getDataTable();
                      if (!curTable) break;

                      const dataRows = Array.from(curTable.querySelectorAll("tr")).filter((r) =>
                        r.querySelector('input[type="checkbox"]')
                      );

                      let checkedCount = 0;
                      for (const row of dataRows) {
                        const cells = Array.from(row.querySelectorAll("td"));
                        const dateText = (cells[verifiedDateColIdx]?.innerText || "").trim();
                        const cb = row.querySelector('input[type="checkbox"]');

                        if (dateText.includes(TARGET_YEAR) && cb && !cb.disabled) {
                          if (!cb.checked) cb.click();
                          checkedCount++;
                        } else if (cb && cb.checked && !cb.disabled) {
                          cb.click();
                        }
                      }

                      if (checkedCount > 0) {
                        logStatus(pName + " | Page " + pageNum + ": Freezing " + checkedCount + " row(s)...");
                        const freezeBtn = findButton(/freeze\s*account/i);
                        if (!freezeBtn) {
                          logStatus("Error: Freeze Account button not found.");
                          window.STOP_FREEZE = true;
                          break;
                        }
                        const sigBefore = getPageSignature();
                        freezeBtn.click();
                        await waitForPostback(sigBefore);
                      } else {
                        logStatus(pName + " | Page " + pageNum + ": 0 rows left for 2026.");
                        break;
                      }
                    }

                    if (window.STOP_FREEZE) break;

                    const nextLink = getNextPageLink();
                    if (nextLink) {
                      pageNum++;
                      logStatus(pName + " -> Moving to Page " + pageNum + "...");
                      const sigBefore = getPageSignature();
                      nextLink.click();
                      await waitForPostback(sigBefore);
                    } else {
                      break;
                    }
                  }
                }

                if (!window.STOP_FREEZE) {
                  localStorage.removeItem(STORAGE_KEY);
                  if (window.AndroidBridge && window.AndroidBridge.onFinished) {
                    window.AndroidBridge.onFinished();
                  }
                }
              } finally {
                window.__VBGRAMG_WORKER_ACTIVE = false;
              }
            })();
        """.trimIndent()

        webView.evaluateJavascript(jsCode, null)
    }

    inner class AndroidBridge {
        @JavascriptInterface
        fun onStatus(message: String) {
            updateStatus(message)
        }

        @JavascriptInterface
        fun onFinished() {
            isAutomationRunning = false
            runOnUiThread {
                btnStart.isEnabled = true
                btnStop.isEnabled = false
                tvStatus.text = "Status: Completed all Panchayats in Block!"
                Toast.makeText(
                    this@MainActivity,
                    "Completed freezing all 2026 accounts!",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
