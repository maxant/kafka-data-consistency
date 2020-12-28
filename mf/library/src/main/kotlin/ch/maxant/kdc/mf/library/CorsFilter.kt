package ch.maxant.kdc.mf.library

import ch.maxant.kdc.mf.library.Context.Companion.DEMO_CONTEXT
import ch.maxant.kdc.mf.library.Context.Companion.REQUEST_ID
import javax.servlet.Filter
import javax.servlet.FilterChain
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.annotation.WebFilter
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@WebFilter(urlPatterns = ["/*"], asyncSupported = true)
class CorsFilter : Filter {
    //https://stackoverflow.com/questions/10636611/how-does-access-control-allow-origin-header-work/10636765#10636765
    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        val req = request as HttpServletRequest
        val res = response as HttpServletResponse
        res.addHeader("Access-Control-Allow-Origin", "*")
        if ("OPTIONS" == req.method) {
            // quarkus maps ForbiddenException/NotAuthorizedException to the www-authenticate header
            // unclear if it belongs in allow-headers, or expose-headers: https://stackoverflow.com/a/44816592/458370
            res.addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, HEAD")
            res.addHeader("Access-Control-Allow-Headers", "www-authenticate, ${SecurityHeaderName}, content-type, elastic-apm-traceparent, $REQUEST_ID, $DEMO_CONTEXT")
            res.addHeader("access-control-expose-headers", "www-authenticate, $REQUEST_ID") // TODO this doesnt work - no access to www-authenticate header :-(
            res.status = 200
        } else {
            chain.doFilter(request, response)
        }
    }
}