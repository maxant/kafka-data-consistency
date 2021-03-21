package ch.maxant.kdc.mf.contracts.control

import ch.maxant.kdc.mf.contracts.adapter.DiscountSurcharge
import ch.maxant.kdc.mf.contracts.entity.ComponentEntity
import ch.maxant.kdc.mf.contracts.entity.ContractEntity
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.quarkus.redis.client.RedisClient
import org.eclipse.microprofile.metrics.MetricUnits
import org.eclipse.microprofile.metrics.annotation.Timed
import org.eclipse.microprofile.opentracing.Traced
import org.jboss.logging.Logger
import java.util.*
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject

@ApplicationScoped
@Timed(unit = MetricUnits.MILLISECONDS)
@Traced
class RedisRepo(
        @Inject val redis: RedisClient,
        @Inject val om: ObjectMapper
) {
    val log: Logger = Logger.getLogger(this.javaClass)

    fun getContract(contractId: UUID): ContractEntity = getContractIfExists(contractId)!!

    fun getContractIfExists(contractId: UUID): ContractEntity? {
        val rr = redis.get("${contractId}-contract")
        if(rr != null) {
            return om.readValue<ContractEntity>(rr.toString())
        }
        return null
    }

    fun setContract(contract: ContractEntity) {
        redis.set(listOf("${contract.id}-contract", om.writeValueAsString(contract)))
    }

    fun getComponents(contractId: UUID) = om.readValue<List<ComponentEntity>>(redis.get("${contractId}-components").toString())

    fun setComponents(components: List<ComponentEntity>) {
        redis.set(listOf("${components[0].contractId}-components", om.writeValueAsString(components)))
    }

    fun withRedisRollback(contractId: UUID, fn: () -> ContractEntity): ContractEntity {
        val contract = redis.get("$contractId-contract")
        val components = redis.get("$contractId-components")
        return try {
            fn()
        } catch (e: Exception) {
            log.warn("rolling back redis for contract $contractId", e)
            if(contract != null) redis.set(listOf("$contractId-contract", contract.toString()))
            if(components != null) redis.set(listOf("$contractId-components", components.toString()))
            throw e
        }
    }

    fun getDscs(contractId: UUID): List<DiscountSurcharge> {
        val rr = redis.get("$contractId-dsc")
        return if(rr == null) emptyList()
            else om.readValue(rr.toString())
    }
}

