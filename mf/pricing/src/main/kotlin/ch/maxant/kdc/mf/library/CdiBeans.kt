package ch.maxant.kdc.mf.library

import org.jboss.logging.Logger
import javax.enterprise.inject.Produces

class CdiBeans {

    @Produces
    fun logger() = Logger.getLogger(Class.forName(Thread.currentThread().stackTrace[1].className))

}