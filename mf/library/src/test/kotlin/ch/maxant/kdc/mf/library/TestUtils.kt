package ch.maxant.kdc.mf.library

import javax.persistence.EntityManager

class TestUtils {

    companion object {
        fun <T> flushed(em: EntityManager, f: ()->T) =
                try {
                    f()
                } catch(e: Exception) {
                    throw e
                } finally {
                    em.flush()
                    em.clear()
                }
    }
}
