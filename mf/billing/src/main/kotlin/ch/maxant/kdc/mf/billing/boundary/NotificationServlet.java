package ch.maxant.kdc.mf.billing.boundary;

import javax.inject.Inject;
import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.UUID;

/**
 * use how made servlet, because during load tests, mutiny wasnt working too well
 */
@WebServlet(asyncSupported = true, urlPatterns = {"/notifications"})
public class NotificationServlet extends HttpServlet {

    @Inject
    BillingStreamApplication billingStreamApplication;

	public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {

        // https://serverfault.com/questions/801628/for-server-sent-events-sse-what-nginx-proxy-configuration-is-appropriate
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Transfer-Encoding", "chunked");
		response.setContentType("text/event-stream;element-type=\"application/json\"");
		response.setCharacterEncoding("UTF-8");

		PrintWriter writer = response.getWriter();

		UUID id = UUID.randomUUID();
        AsyncContext async = request.startAsync();
        billingStreamApplication.getSubscriptions().put(id.toString(), new EmitterState(async, writer, System.currentTimeMillis()));
        async.setTimeout(0); // no timeout
        async.addListener(new AsyncListener() {
            @Override
            public void onComplete(AsyncEvent event) {
                billingStreamApplication.getSubscriptions().remove(id.toString());
            }

            @Override
            public void onTimeout(AsyncEvent event) {
                try {
                    async.complete();
                } catch(Exception e) {
                    // no worries
                    e.printStackTrace();
                }
                billingStreamApplication.getSubscriptions().remove(id.toString());
            }

            @Override
            public void onError(AsyncEvent event) {
                try {
                    async.complete();
                } catch(Exception e) {
                    // no worries
                    e.printStackTrace();
                }
                billingStreamApplication.getSubscriptions().remove(id.toString());
            }

            @Override
            public void onStartAsync(AsyncEvent event) { // noop
            }
        });

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
