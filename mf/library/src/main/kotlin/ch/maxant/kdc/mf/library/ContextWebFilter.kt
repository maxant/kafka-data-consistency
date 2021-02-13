package ch.maxant.kdc.mf.library

import ch.maxant.kdc.mf.library.Context.Companion.COMMAND
import ch.maxant.kdc.mf.library.Context.Companion.DEMO_CONTEXT
import ch.maxant.kdc.mf.library.Context.Companion.REQUEST_ID
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.opentracing.Scope
import io.opentracing.Tracer
import org.apache.commons.lang3.StringUtils
import org.jboss.logging.Logger
import org.jboss.logging.MDC
import org.jboss.resteasy.core.ResteasyContext
import java.util.*
import javax.inject.Inject
import javax.servlet.Filter
import javax.servlet.FilterChain
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.annotation.WebFilter
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@WebFilter(asyncSupported = true, urlPatterns = ["*"])
@SuppressWarnings("unused")
class ContextWebFilter: Filter {

    @Inject
    lateinit var context: Context

    @Inject
    lateinit var om: ObjectMapper

    @Inject
    lateinit var tracer: Tracer

    val log: Logger = Logger.getLogger(this.javaClass)

    override fun doFilter(req: ServletRequest, res: ServletResponse, filterChain: FilterChain) {
        val request = req as HttpServletRequest
        val response = res as HttpServletResponse

        context.requestId = getRequestId(request)
        context.demoContext = readDemoContext(request)

        MDC.put(REQUEST_ID, context.requestId)
        val cmd = "${request.method} ${request.requestURI}"
        MDC.put(COMMAND, cmd)

        ensureResteasyWillWork()

        var scope: Scope? = if(tracer.activeSpan() == null) {
            val scope = tracer.buildSpan(cmd).startActive(true)
            scope
        } else {
            null
        }
        tracer.activeSpan().setTag(REQUEST_ID, context.requestId.toString())

        try {
            filterChain.doFilter(request, response)
        } finally {
            response.setHeader(REQUEST_ID, context.requestId.toString())
            MDC.clear()
            scope?.close()
        }
    }

    private fun ensureResteasyWillWork() {
        // TODO this is really really strange, perhaps somehow related to the problems encountered in KafkaConsumers?
        // anyway, just try and set something in the context data map. if it fails, it will fail downstream, so
        // set it up to contain a HashMap rather than an EmptyMap
        val datamap = ResteasyContext.getContextDataMap(false)
        if(datamap != null) {
            try {
                datamap[this::class.java] = "TEST"
            } catch(e: UnsupportedOperationException) { // this is what happens when it fails
                ResteasyContext.pushContextDataMap(HashMap())
            } finally {
                datamap.remove(this::class.java)
            }
        }
    }

    private fun getRequestId(request: HttpServletRequest): RequestId {
        val requestId = request.getHeader(REQUEST_ID)
        val rId = if (requestId != null && requestId.isNotEmpty())
            requestId
        else {
            // it might already be in the request from another filter
            if(context.isRequestIdAlreadySet()) {
                context.requestId.requestId
            } else {
                val rId2 = context.getRequestIdSafely().requestId
                log.info("creating new requestId as it is missing in the request: $rId2 on path ${request.method} ${request.requestURI}")
                rId2
            }
        }
        return RequestId(rId)
    }

    private fun readDemoContext(request: HttpServletRequest): DemoContext {
        var raw = request.getHeader(DEMO_CONTEXT)
        raw = if(StringUtils.isEmpty(raw)) "{}" else raw
        val dc = om.readValue<DemoContext>(raw)
        dc.json = raw
        return dc
    }
}