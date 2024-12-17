package net.typeblog.lpac_jni

data class AllowedOperators(
    val plmn: String,
    val gid1: String,
    val gid2: String,
)

data class RulesAuthorisationTable(
    val pprIds: List<String>,
    val allowedOperators: List<AllowedOperators>,
    val pprFlags: List<String>,
)
