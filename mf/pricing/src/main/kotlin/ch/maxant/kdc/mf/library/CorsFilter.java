package ch.maxant.kdc.mf.library;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebFilter(urlPatterns = {"/*"})
public class CorsFilter implements Filter {

    //https://stackoverflow.com/questions/10636611/how-does-access-control-allow-origin-header-work/10636765#10636765
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        res.addHeader("Access-Control-Allow-Origin", "*");
        if("OPTIONS".equals(req.getMethod())) {
            res.addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, HEAD");
            res.addHeader("Access-Control-Allow-Headers", "content-type, elastic-apm-traceparent");
            res.setStatus(200);
        } else {
            chain.doFilter(request, response);
        }
    }

}
