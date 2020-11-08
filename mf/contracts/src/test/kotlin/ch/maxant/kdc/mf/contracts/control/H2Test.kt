package ch.maxant.kdc.mf.contracts.control

import org.junit.jupiter.api.Test
import java.sql.DriverManager

class H2Test {

    @Test
    fun h2() {
        // this only works with version 1.4.198 because version *197 removed support for DATETIME with precision - see links in pom
        val conn = DriverManager.getConnection("jdbc:h2:mem:test;MODE=MySQL;DB_CLOSE_DELAY=-1")
        val sql = """CREATE TABLE T_CONTRACTS (
  ID            VARCHAR(36),
  STARTTIME     DATETIME(3) NOT NULL,    -- valid from, inclusive
  ENDTIME       DATETIME(3) NOT NULL,    -- valid to, inclusive
  STATE         VARCHAR(30) NOT NULL,
  PRIMARY KEY(ID)
)
;
"""
        val ps = conn.prepareStatement(sql)
        ps.executeUpdate()
        println("done")
    }

}
