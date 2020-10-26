package ch.maxant.kdc.mf.pricing.dto;

data class Component(
        val configs: List<Configuration>,
        val children: List<Component>
)
