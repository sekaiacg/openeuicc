package im.angry.openeuicc.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import im.angry.openeuicc.common.R
import im.angry.openeuicc.core.EuiccChannel
import im.angry.openeuicc.core.EuiccChannelManager
import im.angry.openeuicc.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class EuiccInfo2Activity : BaseEuiccAccessActivity(), OpenEuiccContextMarker {
    private lateinit var euiccInfo2ItemList: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private val euiccInfo2Adapter = EuiccInfo2Adapter()

    private lateinit var euiccChannel: EuiccChannel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_euicc_chip_info)
        setSupportActionBar(requireViewById(R.id.toolbar))
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        swipeRefresh = requireViewById(R.id.swipe_refresh)
        euiccInfo2ItemList = requireViewById(R.id.recycler_view)

        setupRootViewInsets(euiccInfo2ItemList)
    }

    override fun onInit() {
        euiccChannel = euiccChannelManager
            .findEuiccChannelBySlotBlocking(intent.getIntExtra("logicalSlotId", 0))!!

        euiccInfo2ItemList.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        euiccInfo2ItemList.addItemDecoration(
            DividerItemDecoration(
                this,
                LinearLayoutManager.VERTICAL
            )
        )
        euiccInfo2ItemList.adapter = euiccInfo2Adapter

        // This is slightly different from the MainActivity logic
        // due to the length (we don't want to display the full USB product name)
        val channelTitle = if (euiccChannel.logicalSlotId == EuiccChannelManager.USB_CHANNEL_ID) {
            getString(R.string.usb)
        } else {
            getString(R.string.channel_name_format, euiccChannel.logicalSlotId)
        }

        title = getString(R.string.euicc_chip_detailed_format, channelTitle)

        swipeRefresh.setOnRefreshListener { refresh() }

        refresh()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.activity_euicc_chip, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
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

    private fun launchTask(task: suspend () -> Unit) {
        swipeRefresh.isRefreshing = true

        lifecycleScope.launch {
            task()

            swipeRefresh.isRefreshing = false
        }
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

    data class EuiccInfo2ItemWrapper(
        val title: Int,
        val text: String
    )

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
        launchTask {
            euiccInfo2Adapter.items =
                withContext(Dispatchers.IO) {
                    val euiccInfo2 = euiccChannel.lpa.euiccInfo2
                    val euiccConfiguredAddresses = euiccChannel.lpa.euiccConfiguredAddresses
                    val items = arrayListOf<EuiccInfo2ItemWrapper>()
                    val eid = euiccChannel.lpa.eID
                    items.add(EuiccInfo2ItemWrapper(R.string.euicc_info_eid_title, eid))
                    getManufacturerInfoV2(eid).let {
                        if (it.isNotBlank()) {
                            items.add(EuiccInfo2ItemWrapper(R.string.euicc_info_manufacturer_title, it))
                        }
                    }
                    euiccInfo2?.let {
                        with(items) {
                            add(EuiccInfo2ItemWrapper(R.string.euicc_info_gsma_sgp_22_version_title, it.svn))
                            add(EuiccInfo2ItemWrapper(R.string.euicc_info_euicc_sas_accreditation_number_title, it.sasAccreditationNumber))
                            add(EuiccInfo2ItemWrapper(R.string.euicc_info_euicc_firmware_version_title, it.euiccFirmwareVersion))

                            val resources = getResources()
                            add(EuiccInfo2ItemWrapper(R.string.euicc_info_ext_card_resource_title,
                                resources.getString(R.string.euicc_info_ext_card_resource,
                                    it.installedApplication,
                                    formatFreeSpace(it.freeNvram),
                                    formatFreeSpace(it.freeRam)))
                            )

                            add(EuiccInfo2ItemWrapper(R.string.euicc_info_tca_version_title, it.profileVersion))
                            add(EuiccInfo2ItemWrapper(R.string.euicc_info_ts102241_version_title, it.ts102241Version))
                            add(EuiccInfo2ItemWrapper(R.string.euicc_info_globalplatform_version_title, it.globalPlatformVersion))
                            add(EuiccInfo2ItemWrapper(R.string.euicc_info_protection_profile_version_title, it.ppVersion))

                            add(EuiccInfo2ItemWrapper(R.string.euicc_info_rsp_capability_title, it.rspCapability.joinToString(separator = ", ")))
                            add(EuiccInfo2ItemWrapper(R.string.euicc_info_uicc_capability_title, it.uiccCapability.joinToString(separator = ", ")))
                            add(EuiccInfo2ItemWrapper(R.string.euicc_info_pki_ids_title, parsePkiList(it.euiccCiPKIdListForSigning, it.euiccCiPKIdListForVerification)))
                            add(EuiccInfo2ItemWrapper(R.string.euicc_info_certificationDataObject_title,
                                resources.getString(R.string.euicc_info_certificationDataObject, it.platformLabel, it.discoveryBaseURL))
                            )
                            euiccConfiguredAddresses?.let {
                                add(
                                    EuiccInfo2ItemWrapper(
                                        R.string.euicc_info_configured_addresses_title,
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
                    items
                }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    inner class EuiccInfo2ViewHolder(private val root: View) : RecyclerView.ViewHolder(root) {
        private val title: TextView = root.requireViewById(R.id.euicc_info_item_title)
        private val text: TextView = root.requireViewById(R.id.euicc_info_item_text)
        fun updateNotification(value: EuiccInfo2ItemWrapper) {
            title.setText(value.title)
            text.text = value.text
        }
    }

    inner class EuiccInfo2Adapter : RecyclerView.Adapter<EuiccInfo2ViewHolder>() {
        var items: List<EuiccInfo2ItemWrapper> = listOf()
            @SuppressLint("NotifyDataSetChanged")
            set(value) {
                field = value
                notifyDataSetChanged()
            }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EuiccInfo2ViewHolder {
            val root = LayoutInflater.from(parent.context)
                .inflate(R.layout.activity_euicc_chip_item, parent, false)
            return EuiccInfo2ViewHolder(root)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: EuiccInfo2ViewHolder, position: Int) =
            holder.updateNotification(items[position])

    }
}
