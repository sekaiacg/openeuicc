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
import im.angry.openeuicc.core.ApduInterfaceEstkmeInfoProvider
import im.angry.openeuicc.core.EuiccChannel
import im.angry.openeuicc.core.EuiccChannelManager
import im.angry.openeuicc.util.*
import im.angry.openeuicc.vendored.ESTKmeInfo
import im.angry.openeuicc.vendored.getESTKmeInfo
import im.angry.openeuicc.vendored.getSIMLinkVersion
import kotlinx.coroutines.launch
import net.typeblog.lpac_jni.impl.PKID_GSMA_LIVE_CI
import net.typeblog.lpac_jni.impl.PKID_GSMA_TEST_CI
import java.io.BufferedReader
import java.io.InputStreamReader

class EuiccInfoActivity : BaseEuiccAccessActivity(), OpenEuiccContextMarker {
    companion object {
        private val YES_NO = Pair(R.string.yes, R.string.no)
    }

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var infoList: RecyclerView

    private var logicalSlotId: Int = -1

    data class Item(
        @StringRes
        val titleResId: Int,
        var content: String?,
        val copiedToastResId: Int? = null,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_euicc_info)
        setSupportActionBar(requireViewById(R.id.toolbar))
        setupToolbarInsets()
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        swipeRefresh = requireViewById(R.id.swipe_refresh)
        infoList = requireViewById<RecyclerView>(R.id.recycler_view).also {
            it.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
            it.addItemDecoration(DividerItemDecoration(this, LinearLayoutManager.VERTICAL))
            it.adapter = EuiccInfoAdapter()
        }

        logicalSlotId = intent.getIntExtra("logicalSlotId", 0)

        val channelTitle = if (logicalSlotId == EuiccChannelManager.USB_CHANNEL_ID) {
            getString(R.string.usb)
        } else {
            appContainer.customizableTextProvider.formatInternalChannelName(logicalSlotId)
        }

        title = getString(R.string.euicc_info_activity_title, channelTitle)

        swipeRefresh.setOnRefreshListener { refresh() }

        setupRootViewInsets(infoList)
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
            (infoList.adapter!! as EuiccInfoAdapter).euiccInfoItems =
                euiccChannelManager.withEuiccChannel(logicalSlotId, ::buildEuiccInfoItems)

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
        val eumDataList: List<EumData> = Gson().fromJson(eumJsonString, object : TypeToken<List<EumData>>(){}.type)
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
        val eID = channel.lpa.eID
        val euiccInfo2 = channel.lpa.euiccInfo2
        var showEum = true
        add(Item(R.string.euicc_info_access_mode, channel.type))
        add(Item(R.string.euicc_info_removable, formatByBoolean(channel.port.card.isRemovable, YES_NO)))
        add(Item(R.string.euicc_info_eid, eID, copiedToastResId = R.string.toast_eid_copied))
        val apduInterface  = channel.apduInterface as? ApduInterfaceEstkmeInfoProvider
        var estkmeInfo :ESTKmeInfo? = null
        estkmeInfo = if (apduInterface != null) {
            apduInterface.estkmeInfo
        } else {
            getESTKmeInfo(channel.apduInterface)
        }
        estkmeInfo?.let {
                add(Item(R.string.euicc_info_sku, it.skuName))
                add(Item(R.string.euicc_info_sn, it.serialNumber, copiedToastResId = R.string.toast_sn_copied))
                add(Item(R.string.euicc_info_bl_ver, it.bootloaderVersion))
                add(Item(R.string.euicc_info_fw_ver, it.firmwareVersion))
                showEum = false
        }
        getSIMLinkVersion(eID, euiccInfo2?.euiccFirmwareVersion)?.let {
            add(Item(R.string.euicc_info_sku, "9eSIM $it"))
            showEum = false
        }
        if (showEum) {
            add(Item(R.string.euicc_info_manufacturer, getManufacturerInfoV2(eID).ifBlank { getString(R.string.unknown) }))
        }

        euiccInfo2?.let { info ->
            add(Item(R.string.euicc_info_profile_version, info.profileVersion.toString()))
            add(Item(R.string.euicc_info_sgp22_version, info.sgp22Version.toString()))
            add(Item(R.string.euicc_info_firmware_version, info.euiccFirmwareVersion.toString()))
            add(Item(R.string.euicc_info_globalplatform_version, info.globalPlatformVersion.toString()))
            add(Item(R.string.euicc_info_pp_version, info.ppVersion.toString()))
            add(Item(R.string.euicc_info_sas_accreditation_number, info.sasAccreditationNumber))
            add(Item(R.string.euicc_info_free_nvram, info.freeNvram.let(::formatFreeSpace)))
        }
        euiccInfo2?.euiccCiPKIdListForSigning.orEmpty().let { signers ->
            // SGP.28 v1.0, eSIM CI Registration Criteria (Page 5 of 9, 2019-10-24)
            // https://www.gsma.com/newsroom/wp-content/uploads/SGP.28-v1.0.pdf#page=5
            // FS.27 v2.0, Security Guidelines for UICC Profiles (Page 25 of 27, 2024-01-30)
            // https://www.gsma.com/solutions-and-impact/technologies/security/wp-content/uploads/2024/01/FS.27-Security-Guidelines-for-UICC-Credentials-v2.0-FINAL-23-July.pdf#page=25
            val resId = when {
                signers.isEmpty() -> R.string.unknown // the case is not mp, but it's is not common
                PKID_GSMA_LIVE_CI.any(signers::contains) -> R.string.euicc_info_ci_gsma_live
                PKID_GSMA_TEST_CI.any(signers::contains) -> R.string.euicc_info_ci_gsma_test
                else -> R.string.euicc_info_ci_unknown
            }
            add(Item(R.string.euicc_info_ci_type, getString(resId)))
        }
        val atr =  channel.atr?.encodeHex() ?: getString(R.string.information_unavailable)
        add(Item(R.string.euicc_info_atr, atr, copiedToastResId = R.string.toast_atr_copied))

        euiccInfo2?.apply {
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
            // euicc_info_certificationDataObject
            add(Item(R.string.euicc_info_certification_data_object,
                getString(R.string.euicc_info_certification_data_object_content,
                    formatByBlank(platformLabel),
                    formatByBlank(discoveryBaseURL)
                )
            ))
        }
        channel.lpa.euiccConfiguredAddresses?.apply {
            add(Item(R.string.euicc_info_configured_addresses,
                getString(
                    R.string.euicc_info_configured_addresses_content,
                    formatByBlank(defaultDpAddress),
                    formatByBlank(rootDsAddress)
                )
            ))
        }
    }

    private fun formatByBoolean(b: Boolean, res: Pair<Int, Int>): String =
        getString(
            if (b) {
                res.first
            } else {
                res.second
            }
        )

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
            content.text = item.content ?: getString(R.string.unknown)
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