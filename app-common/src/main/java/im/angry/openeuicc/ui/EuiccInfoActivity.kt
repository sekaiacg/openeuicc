package im.angry.openeuicc.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import im.angry.openeuicc.common.R
import im.angry.openeuicc.core.ApduInterfaceEuiccInfoProvider
import im.angry.openeuicc.core.EuiccChannel
import im.angry.openeuicc.core.EuiccChannelManager
import im.angry.openeuicc.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.typeblog.lpac_jni.impl.PKID_GSMA_LIVE_CI
import net.typeblog.lpac_jni.impl.PKID_GSMA_TEST_CI
import java.io.BufferedReader
import java.io.InputStreamReader

// https://euicc-manual.osmocom.org/docs/pki/eum/accredited.json
// ref: <https://regex101.com/r/5FFz8u>
private val RE_SAS = Regex(
    """^[A-Z]{2}-[A-Z]{2}(?:-UP)?-\d{4}T?(?:-\d+)?T?$""",
    setOf(RegexOption.IGNORE_CASE),
)

class EuiccInfoActivity : BaseEuiccAccessActivity(), OpenEuiccContextMarker {
    companion object {
        private val YES_NO = Pair(R.string.euicc_info_yes, R.string.euicc_info_no)
    }

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var infoList: RecyclerView

    private var logicalSlotId: Int = -1
    private var seId: EuiccChannel.SecureElementId = EuiccChannel.SecureElementId.DEFAULT

    data class Item(
        @get:StringRes
        val titleResId: Int,
        val content: String?,
        val copiedToastResId: Int? = null,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_euicc_info)
        setSupportActionBar(requireViewById(R.id.toolbar))
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        swipeRefresh = requireViewById(R.id.swipe_refresh)
        infoList = requireViewById<RecyclerView>(R.id.recycler_view).also {
            it.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
            it.addItemDecoration(DividerItemDecoration(this, LinearLayoutManager.VERTICAL))
            it.adapter = EuiccInfoAdapter()
        }

        logicalSlotId = intent.getIntExtra("logicalSlotId", 0)
        seId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("seId", EuiccChannel.SecureElementId::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("seId")
        } ?: EuiccChannel.SecureElementId.DEFAULT

        val channelTitle = if (logicalSlotId == EuiccChannelManager.USB_CHANNEL_ID) {
            getString(R.string.channel_type_usb)
        } else {
            appContainer.customizableTextProvider.formatNonUsbChannelName(logicalSlotId)
        }

        title = getString(R.string.euicc_info_activity_title, channelTitle)

        swipeRefresh.setOnRefreshListener { refresh() }

        setupRootViewSystemBarInsets(
            window.decorView.rootView, arrayOf(
                this::activityToolbarInsetHandler,
                mainViewPaddingInsetHandler(infoList)
            )
        )
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> {
            finish()
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    override fun onInit() {
        refresh()
    }

    private fun refresh() {
        swipeRefresh.isRefreshing = true

        lifecycleScope.launch {
            euiccChannelManager.withEuiccChannel(logicalSlotId, seId) { channel ->
                // When the chip multi-SE, we need to include seId in the title (because we don't have access
                // to hasMultipleSE in the onCreate() function, we need to do it here).
                // TODO: Move channel formatting to somewhere centralized and remove this hack. (And also, of course, add support for USB)
                if (channel.hasMultipleSE && logicalSlotId != EuiccChannelManager.USB_CHANNEL_ID) {
                    withContext(Dispatchers.Main) {
                        title =
                            appContainer.customizableTextProvider.formatNonUsbChannelNameWithSeId(logicalSlotId, seId)
                    }
                }

                val items = buildEuiccInfoItems(channel)

                withContext(Dispatchers.Main) {
                    (infoList.adapter!! as EuiccInfoAdapter).euiccInfoItems = items
                }
            }

            swipeRefresh.isRefreshing = false
        }
    }

    data class Product(
        val prefix: String,
        @SerializedName("in-range")
        val inRange: List<List<Int>>?,
        val name: String
    )

    data class EumData(
        val eum: String,
        val country: String,
        val manufacturer: String,
        val products: List<Product>?
    )

    private fun getManufacturerInfoV2(eid: String): String {
        // https://euicc-manual.osmocom.org/docs/pki/eum/manifest-v2.json
        val eumJsonString = BufferedReader(InputStreamReader(getResources().assets.open("eum_v2.json")))
            .useLines { lines ->
                val results = StringBuilder()
                lines.forEach { results.append(it) }
                results.toString()
            }
        val eumDataList: List<EumData> = Gson().fromJson(eumJsonString, object : TypeToken<List<EumData>>() {}.type)
        eumDataList.find { eid.startsWith(it.eum) }?.apply {
            products?.forEach { p ->
                if (eid.startsWith(p.prefix)) {
                    if (p.inRange != null) {
                        p.inRange.forEach { ir ->
                            val eidNum = eid.substring(p.prefix.length, eid.length - 2).toInt()
                            if (eidNum in ir[0]..ir[1]) return "$manufacturer(${country}): ${p.name}"
                        }
                    } else return "$manufacturer(${country}): ${p.name}"
                }
            }
            return "$manufacturer(${country})"
        }
        return ""
    }

    private fun buildEuiccInfoItems(channel: EuiccChannel) = buildList {
        var showEum = true
        val eID = channel.lpa.eID
        val euiccInfo2 = channel.lpa.euiccInfo2
        add(Item(R.string.euicc_info_access_mode, channel.type))
        add(Item(R.string.euicc_info_removable, formatByBoolean(channel.port.card.isRemovable, YES_NO)))
        add(Item(R.string.euicc_info_eid, eID, copiedToastResId = R.string.toast_eid_copied))
        if (!channel.isdrAid.contentEquals(EUICC_DEFAULT_ISDR_AID.decodeHex())) {
            // Only show if it's not the default ISD-R AID
            add(Item(R.string.euicc_info_isdr_aid, channel.isdrAid.encodeHex()))
        }
        val apduInterface = channel.apduInterface as? ApduInterfaceEuiccInfoProvider
        val vendorInfo: EuiccVendorInfo? = if (apduInterface != null) {
            apduInterface.euiccVendorInfo
        } else {
            channel.tryParseEuiccVendorInfo()
        }
        vendorInfo?.let {
            // @formatter:off
            vendorInfo.skuName?.let { add(Item(R.string.euicc_info_sku, it)) }
            vendorInfo.serialNumber?.let { add(Item(R.string.euicc_info_sn, it, copiedToastResId = R.string.toast_sn_copied)) }
            vendorInfo.firmwareVersion?.let {
                var firmwareVersion = vendorInfo.firmwareVersion
                vendorInfo.region?.let {
                    val region = when (it) {
                            0x00 -> getString(R.string.euicc_info_region_worldwide)
                            0x01 -> getString(R.string.euicc_info_region_zh_cn)
                            0x02 -> getString(R.string.euicc_info_region_zh_hk)
                            0x03 -> getString(R.string.euicc_info_region_ja)
                            else -> "??"
                        }
                    firmwareVersion += " (${region})"
                }
                add(Item(R.string.euicc_info_fw_ver, firmwareVersion))
            }
            // @formatter:on
            showEum = false
        }
        // @formatter:off
        if (showEum) add(Item(R.string.euicc_info_manufacturer, getManufacturerInfoV2(eID).ifBlank { getString(R.string.euicc_info_unknown) }))
        // @formatter:on
        euiccInfo2?.let { info ->
            add(Item(R.string.euicc_info_profile_version, info.profileVersion.toString()))
            add(Item(R.string.euicc_info_sgp22_version, info.sgp22Version.toString()))
            add(Item(R.string.euicc_info_firmware_version, info.euiccFirmwareVersion.toString()))
            add(Item(R.string.euicc_info_gp_version, info.globalPlatformVersion.toString()))
            add(Item(R.string.euicc_info_pp_version, info.ppVersion.toString()))
            info.sasAccreditationNumber.trim().takeIf(RE_SAS::matches)
                ?.let { add(Item(R.string.euicc_info_sas_accreditation_number, it.uppercase())) }

            val nvramText = buildString {
                append(formatFreeSpace(info.freeNvram))
                append(' ')
                append(getString(R.string.euicc_info_free_nvram_hint))
            }
            add(Item(R.string.euicc_info_free_nvram, nvramText))
        }
        euiccInfo2?.euiccCiPKIdListForSigning.orEmpty().let { signers ->
            // SGP.28 v1.0, eSIM CI Registration Criteria (Page 5 of 9, 2019-10-24)
            // https://www.gsma.com/newsroom/wp-content/uploads/SGP.28-v1.0.pdf#page=5
            // FS.27 v2.0, Security Guidelines for UICC Profiles (Page 25 of 27, 2024-01-30)
            // https://www.gsma.com/solutions-and-impact/technologies/security/wp-content/uploads/2024/01/FS.27-Security-Guidelines-for-UICC-Credentials-v2.0-FINAL-23-July.pdf#page=25
            val resId = when {
                signers.isEmpty() -> R.string.euicc_info_unknown // the case is not mp, but it's is not common
                PKID_GSMA_LIVE_CI.any(signers::contains) -> R.string.euicc_info_ci_gsma_live
                PKID_GSMA_TEST_CI.any(signers::contains) -> R.string.euicc_info_ci_gsma_test
                else -> R.string.euicc_info_ci_unknown
            }
            add(Item(R.string.euicc_info_ci_type, getString(resId)))
        }
        val atr = channel.atr?.encodeHex() ?: getString(R.string.euicc_info_unavailable)
        add(Item(R.string.euicc_info_atr, atr, copiedToastResId = R.string.toast_atr_copied))

        euiccInfo2?.apply {
            // @formatter:off
            //euicc_info_ext_card_resource
            add(Item(R.string.euicc_info_ext_card_resource,
                getString(R.string.euicc_info_ext_card_resource_content,
                    installedApplication,
                    freeNvram.toString(),
                    freeRam.toString()
                )
            ))
            add(Item(R.string.euicc_info_uicc_capability, uiccCapability.joinToString(separator = ", ")))
            //euicc_info_rsp_capability
            add(Item(R.string.euicc_info_rsp_capability, rspCapability.joinToString(separator = ", ")))
            //euicc_info_forbiddenProfilePolicyRules
            add(Item(R.string.euicc_info_forbidden_profile_policy_rules, forbiddenProfilePolicyRules.joinToString(separator = ", ")))
            // euicc_info_certificationDataObject
            add(Item(R.string.euicc_info_certification_data_object,
                getString(R.string.euicc_info_certification_data_object_content,
                    formatByBlank(platformLabel),
                    formatByBlank(discoveryBaseURL)
                )
            ))
            // @formatter:on
        }

        channel.lpa.rulesAuthorisationTable.let {
            if (it.isNotEmpty()) {
                val r = buildString {
                    it.forEachIndexed { idx, item ->
                        val pprIds = item.pprIds.joinToString(", ")
                        val pprFlags = item.pprFlags.joinToString(",")
                        val tableSym = "\t\t\t\t"
                        val allowedOperators = buildString {
                            item.allowedOperators.forEach { a ->
                                append("${tableSym}PLMN: ${a.plmn}\n")
                                append("${tableSym}Gid1: ${formatByBlank(a.gid1)}\n")
                                append("${tableSym}Gid2: ${formatByBlank(a.gid2)}\n")
                            }
                        }
                        append("index: ${idx + 1}\n")
                        append("PPR IDs: $pprIds\n")
                        append("Operators: \n$allowedOperators")
                        append("PPR Flags: $pprFlags")
                        if (idx + 1 < it.size) append("\n")
                    }
                }
                add(Item(R.string.euicc_info_rules_authorisation_table, r))
            }
        }

        channel.lpa.euiccConfiguredAddresses?.apply {
            // @formatter:off
            add(Item(R.string.euicc_info_configured_addresses,
                getString(
                    R.string.euicc_info_configured_addresses_content,
                    formatByBlank(defaultDpAddress),
                    formatByBlank(rootDsAddress)
                )
            ))
            // @formatter:on
        }
    }

    @Suppress("SameParameterValue")
    private fun formatByBoolean(b: Boolean, res: Pair<Int, Int>): String =
        getString(if (b) res.first else res.second)

    private fun formatByBlank(res: String): String =
        res.ifBlank { "N/A" }

    inner class EuiccInfoViewHolder(root: View) : ViewHolder(root) {
        private val title: TextView = root.requireViewById(R.id.euicc_info_title)
        private val content: TextView = root.requireViewById(R.id.euicc_info_content)
        private var copiedToastResId: Int? = null

        init {
            root.setOnClickListener {
                if (copiedToastResId != null) {
                    val label = title.text.toString()
                    getSystemService(ClipboardManager::class.java)!!
                        .setPrimaryClip(ClipData.newPlainText(label, content.text))
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                        Toast.makeText(
                            this@EuiccInfoActivity,
                            copiedToastResId!!,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

        fun bind(item: Item) {
            copiedToastResId = item.copiedToastResId
            title.setText(item.titleResId)
            content.text = item.content ?: getString(R.string.euicc_info_unknown)
        }
    }

    inner class EuiccInfoAdapter : RecyclerView.Adapter<EuiccInfoViewHolder>() {
        var euiccInfoItems: List<Item> = listOf()
            @SuppressLint("NotifyDataSetChanged")
            set(newVal) {
                field = newVal
                notifyDataSetChanged()
            }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EuiccInfoViewHolder {
            val root = LayoutInflater.from(parent.context)
                .inflate(R.layout.euicc_info_item, parent, false)
            return EuiccInfoViewHolder(root)
        }

        override fun getItemCount(): Int = euiccInfoItems.size

        override fun onBindViewHolder(holder: EuiccInfoViewHolder, position: Int) {
            holder.bind(euiccInfoItems[position])
        }
    }
}
