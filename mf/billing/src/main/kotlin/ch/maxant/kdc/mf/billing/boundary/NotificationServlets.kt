package ch.maxant.kdc.mf.billing.boundary

import java.util.*
import javax.inject.Inject
import javax.servlet.AsyncEvent
import javax.servlet.AsyncListener
import javax.servlet.annotation.WebServlet
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * use home made servlet, because during load tests, mutiny wasnt working too well
 */
@WebServlet(asyncSupported = true, urlPatterns = ["/notifications"])
class NotificationServlet(
    @Inject
    val billingStreamApplication: BillingStreamApplication
) : HttpServlet() {

    public override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {

        // https://serverfault.com/questions/801628/for-server-sent-events-sse-what-nginx-proxy-configuration-is-appropriate
        response.setHeader("Cache-Control", "no-cache")
        response.setHeader("X-Accel-Buffering", "no")
        response.setHeader("Transfer-Encoding", "chunked")
        response.contentType = """text/event-stream;element-type="application/json""""
        response.characterEncoding = "UTF-8"
        val id = UUID.randomUUID()
        val async = request.startAsync()
        billingStreamApplication.subscriptions[id.toString()] = EmitterState(async, response.writer, System.currentTimeMillis())
        async.timeout = 0 // no timeout
        async.addListener(object : AsyncListener {
            override fun onComplete(event: AsyncEvent) {
                billingStreamApplication.subscriptions.remove(id.toString())
            }

            override fun onTimeout(event: AsyncEvent) {
                async.complete()
                billingStreamApplication.subscriptions.remove(id.toString())
            }

            override fun onError(event: AsyncEvent) {
                async.complete()
                billingStreamApplication.subscriptions.remove(id.toString())
            }

            override fun onStartAsync(event: AsyncEvent) { // noop
            }
        })

        // do some async work on a thread managed by the container
        /*
        async.start(() -> {
            while(true) {
                writer.write("data: [1, 2]\n\n");
                writer.flush();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
         */
    }
}