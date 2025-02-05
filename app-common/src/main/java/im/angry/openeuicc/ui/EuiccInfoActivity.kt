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
import im.angry.openeuicc.core.EuiccChannel
import im.angry.openeuicc.core.EuiccChannelManager
import im.angry.openeuicc.util.*
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

    private fun get9eSIMV2orAboveVersion(eID: String, firmwareVer: String): String {
        val v2eIDPrefix = "890440452167274948"
        val v3eIDPrefix = "890440458467274948"
        var ver = ""
        if (!(eID.startsWith(v2eIDPrefix) || eID.startsWith(v3eIDPrefix))) return ver
        val split = firmwareVer.split('.')
        val verCode = StringBuilder()
        if (split.isNotEmpty()) {
            for (e in split) {
                if (e.length < 2) {
                    verCode.append("0".repeat(2 - e.length)).append(e)
                } else {
                    verCode.append(e)
                }
            }
        }
        /**
         * 36.7.2 = v2
         * 36.9.3 = v2.1
         * 36.17.4 = v2s
         * 36.17.39 = v3 Beta(Test) (v3 測試)
         * 36.18.5 = v3 Final (v3 最終) 視為發售版
         */
        val verInt = verCode.toString().toInt()
        ver = when  {
            verInt >= 361805 -> "9eSIM V3"
            verInt >= 361739 -> "9eSIM V3 Beta"
            verInt >= 361704 -> "9eSIM V2S"
            verInt >= 360903 -> "9eSIM V2.1"
            verInt in 0..360702 -> "9eSIM V2"
            else -> ""
        }
        return if (ver.isNotEmpty()) "Kigen(GB): $ver" else ""
    }

    private fun buildEuiccInfoItems(channel: EuiccChannel) = buildList {
        val eID = channel.lpa.eID
        add(Item(R.string.euicc_info_access_mode, channel.type))
        add(
            Item(
                R.string.euicc_info_removable,
                formatByBoolean(channel.port.card.isRemovable, YES_NO)
            )
        )
        add(
            Item(
                R.string.euicc_info_eid,
                eID,
                copiedToastResId = R.string.toast_eid_copied
            )
        )

        val eumItem = Item(R.string.euicc_info_manufacturer, getManufacturerInfoV2(eID).ifBlank { getString(R.string.unknown) })
        add(eumItem)

        val euiccInfo2 = channel.lpa.euiccInfo2
        euiccInfo2?.let { info ->
            get9eSIMV2orAboveVersion(eID, info.euiccFirmwareVersion).apply { if (isNotEmpty()) eumItem.content = this }
            add(Item(R.string.euicc_info_profile_version, info.profileVersion))
            add(Item(R.string.euicc_info_sgp22_version, info.sgp22Version))
            add(Item(R.string.euicc_info_firmware_version, info.euiccFirmwareVersion))
            add(Item(R.string.euicc_info_globalplatform_version, info.globalPlatformVersion))
            add(Item(R.string.euicc_info_pp_version, info.ppVersion))
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
        add(
            Item(
                R.string.euicc_info_atr,
                channel.atr?.encodeHex() ?: getString(R.string.information_unavailable),
                copiedToastResId = R.string.toast_atr_copied,
            )
        )
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