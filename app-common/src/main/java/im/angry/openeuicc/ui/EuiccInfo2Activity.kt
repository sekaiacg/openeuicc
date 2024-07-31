package im.angry.openeuicc.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
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
import im.angry.openeuicc.core.EuiccChannelManager
import im.angry.openeuicc.util.formatFreeSpace
import im.angry.openeuicc.util.setupRootViewInsets
import im.angry.openeuicc.util.setupToolbarInsets
import kotlinx.coroutines.launch
import net.typeblog.lpac_jni.impl.DEFAULT_PKID_GSMA_RSP2_ROOT_CI1
import net.typeblog.lpac_jni.impl.PKID_GSMA_TEST_CI
import java.io.BufferedReader
import java.io.InputStreamReader

class EuiccInfo2Activity : BaseEuiccAccessActivity() {
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var infoList: RecyclerView

    private var logicalSlotId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_euicc_info)
        setSupportActionBar(requireViewById(R.id.toolbar))
        setupToolbarInsets()
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        swipeRefresh = requireViewById(R.id.swipe_refresh)
        infoList = requireViewById(R.id.recycler_view)

        infoList.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        infoList.addItemDecoration(DividerItemDecoration(this, LinearLayoutManager.VERTICAL))
        infoList.adapter = EuiccInfoAdapter()

        logicalSlotId = intent.getIntExtra("logicalSlotId", 0)

        // This is slightly different from the MainActivity logic
        // due to the length (we don't want to display the full USB product name)
        val channelTitle = if (logicalSlotId == EuiccChannelManager.USB_CHANNEL_ID) {
            getString(R.string.usb)
        } else {
            getString(R.string.channel_name_format, logicalSlotId)
        }

        title = getString(R.string.euicc_chip_detailed_format, channelTitle)
        swipeRefresh.setOnRefreshListener { refresh() }

        setupRootViewInsets(infoList)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.activity_euicc_chip, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> {
            finish()
            true
        }
        R.id.help -> {
            AlertDialog.Builder(this, R.style.AlertDialogTheme).apply {
                setMessage(R.string.euicc_chip_help)
                setPositiveButton(android.R.string.ok) { dialog, _ ->
                    dialog.dismiss()
                }
                show()
            }
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onInit() {
        refresh()
    }

    private fun parsePkiIds(pki: Array<String>): String {
        if (pki.isNotEmpty()) {
            val sb = StringBuilder()
            for (pkiId in pki) {
                sb.append("$pkiId\n")
            }
            return sb.subSequence(0, sb.length - 1).toString()
        }
        return "N/A"
    }

    private fun parsePkiList(forSign: Array<String>, forVerify: Array<String>): String {
        val sign = parsePkiIds(forSign)
        val verify = parsePkiIds(forVerify)
        return if (sign == verify) {
            "Sign and Verify:\n$sign"
        } else {
            "Sign: $sign\nVerify: $verify"
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
                    p.inRange?.forEach { ir ->
                        val eidNum = eid.substring(p.prefix.length, eid.length - 2).toInt()
                        if (eidNum in ir[0]..ir[1]) return "$manufacturer(${country}): ${p.name}"
                    }
                }
            }
            return "$manufacturer(${country})"
        }
        return ""
    }

    private fun refresh() {
        swipeRefresh.isRefreshing = true

        lifecycleScope.launch {
            val unknownStr = getString(R.string.unknown)

            val newItems = mutableListOf<Pair<String, String>>()

            euiccChannelManager.withEuiccChannel(logicalSlotId) { channel ->
                newItems.apply {
                    val lpa = channel.lpa
                    val eid = lpa.eID
                    val euiccInfo2 = lpa.euiccInfo2
                    add(Pair(getString(R.string.euicc_info_access_mode), channel.type))
                    add(Pair(getString(R.string.euicc_info_removable), if (channel.port.card.isRemovable) getString(R.string.yes) else getString(R.string.no)))
                    add(Pair(getString(R.string.euicc_info_eid_title), eid))

                    getManufacturerInfoV2(eid).let {
                            add(Pair(getString(R.string.euicc_info_manufacturer_title), it.ifBlank { unknownStr }))
                    }

                    euiccInfo2?.apply {
                        add(Pair(getString(R.string.euicc_info_euicc_firmware_version_title), euiccFirmwareVersion))
                        add(Pair(getString(R.string.euicc_info_euicc_sas_accreditation_number_title), sasAccreditationNumber))
                        add(Pair(getString(R.string.euicc_info_free_nvram), formatFreeSpace(freeNvram)))

                        add(Pair(getString(R.string.euicc_info_gsma_sgp_22_version_title), svn))
                        add(Pair(getString(R.string.euicc_info_globalplatform_version_title), globalPlatformVersion))
                        add(Pair(getString(R.string.euicc_info_protection_profile_version_title), ppVersion))
                        add(Pair(getString(R.string.euicc_info_tca_version_title), profileVersion))
                        add(Pair(getString(R.string.euicc_info_ts102241_version_title), ts102241Version))
                        add(Pair(getString(R.string.euicc_info_gsma_prod),
                            if (euiccCiPKIdListForSigning.contains(DEFAULT_PKID_GSMA_RSP2_ROOT_CI1)) getString(R.string.supported) else getString(R.string.unsupported)
                        ))
                        add(Pair(getString(R.string.euicc_info_gsma_test),
                            if (PKID_GSMA_TEST_CI.any { euiccCiPKIdListForSigning.contains(it) }) getString(
                                    R.string.supported
                                ) else getString(R.string.unsupported)
                        ))
                        add(
                            Pair(getString(R.string.euicc_info_ext_card_resource_title),
                            resources.getString(R.string.euicc_info_ext_card_resource,
                                installedApplication,
                                formatFreeSpace(freeNvram),
                                formatFreeSpace(freeRam)))
                        )
                        add(Pair(getString(R.string.euicc_info_rsp_capability_title), rspCapability.joinToString(separator = ", ")))
                        add(Pair(getString(R.string.euicc_info_uicc_capability_title), uiccCapability.joinToString(separator = ", ")))
                        add(Pair(getString(R.string.euicc_info_pki_ids_title), parsePkiList(euiccCiPKIdListForSigning, euiccCiPKIdListForVerification)))

                        add(Pair(getString(R.string.euicc_info_certificationDataObject_title),
                            resources.getString(R.string.euicc_info_certificationDataObject, platformLabel, discoveryBaseURL))
                        )
                        lpa.euiccConfiguredAddresses?.let {
                            add(Pair(
                                    getString(R.string.euicc_info_configured_addresses_title),
                                    resources.getString(
                                        R.string.euicc_info_configured_addresses,
                                        it.defaultDpAddress,
                                        it.rootDsAddress
                                    )
                                )
                            )
                        }
                    }

                }
            }

            (infoList.adapter!! as EuiccInfoAdapter).euiccInfoItems = newItems

            swipeRefresh.isRefreshing = false
        }
    }

    inner class EuiccInfoViewHolder(root: View) : ViewHolder(root) {
        private val title: TextView = root.requireViewById(R.id.euicc_info_title)
        private val content: TextView = root.requireViewById(R.id.euicc_info_content)

        fun bind(item: Pair<String, String>) {
            title.text = item.first
            content.text = item.second
        }
    }

    inner class EuiccInfoAdapter : RecyclerView.Adapter<EuiccInfoViewHolder>() {
        var euiccInfoItems: List<Pair<String, String>> = listOf()
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