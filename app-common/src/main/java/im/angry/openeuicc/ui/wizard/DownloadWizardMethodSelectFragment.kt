package im.angry.openeuicc.ui.wizard

import android.app.AlertDialog
import android.content.ClipboardManager
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import im.angry.openeuicc.common.R
import im.angry.openeuicc.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DownloadWizardMethodSelectFragment : DownloadWizardActivity.DownloadWizardStepFragment() {
    data class DownloadMethod(
        val iconRes: Int,
        val titleRes: Int,
        val onClick: () -> Unit
    )

    // TODO: Maybe we should find a better barcode scanner (or an external one?)
    private val barcodeScannerLauncher = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let { content ->
            processLpaString(content)
        }
    }

    private val gallerySelectorLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { result ->
            if (result == null) return@registerForActivityResult

            lifecycleScope.launch {
                val decoded = withContext(Dispatchers.IO) {
                    runCatching {
                        requireContext().contentResolver.openInputStream(result)?.use { input ->
                            BitmapFactory.decodeStream(input).use(::decodeQrFromBitmap)
                        }
                    }
                }

                decoded.getOrNull()?.let { processLpaString(it) }
            }
        }

    val downloadMethods = arrayOf(
        DownloadMethod(R.drawable.ic_scan_black, R.string.download_wizard_method_qr_code) {
            barcodeScannerLauncher.launch(ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setOrientationLocked(false)
            })
        },
        DownloadMethod(R.drawable.ic_gallery_black, R.string.download_wizard_method_gallery) {
            gallerySelectorLauncher.launch("image/*")
        },
        DownloadMethod(R.drawable.ic_paste_go, R.string.download_wizard_method_clipboard) {
            handleLoadFromClipboard()
        },
        DownloadMethod(R.drawable.ic_edit, R.string.download_wizard_method_manual) {
            gotoNextFragment(DownloadWizardDetailsFragment())
        }
    )

    override val hasNext: Boolean
        get() = false
    override val hasPrev: Boolean
        get() = true

    override fun createNextFragment(): DownloadWizardActivity.DownloadWizardStepFragment? =
        null

    override fun createPrevFragment(): DownloadWizardActivity.DownloadWizardStepFragment =
        DownloadWizardSlotSelectFragment()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_download_method_select, container, false)
        val recyclerView = view.requireViewById<RecyclerView>(R.id.download_method_list)
        recyclerView.adapter = DownloadMethodAdapter()
        recyclerView.layoutManager =
            LinearLayoutManager(view.context, LinearLayoutManager.VERTICAL, false)
        recyclerView.addItemDecoration(
            DividerItemDecoration(
                requireContext(),
                LinearLayoutManager.VERTICAL
            )
        )
        return view
    }

    private fun handleLoadFromClipboard() {
        val clipboard = requireContext().getSystemService(ClipboardManager::class.java)
        val text = clipboard.primaryClip?.getItemAt(0)?.text

        if (text == null) {
            Toast.makeText(
                requireContext(),
                R.string.profile_download_no_lpa_string,
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        processLpaString(text.toString())
    }

    private fun processLpaString(input: String) {
        try {
            val parsed = LPAString.parse(input)
            state.smdp = parsed.address
            state.matchingId = parsed.matchingId
            state.confirmationCodeRequired = parsed.confirmationCodeRequired
            gotoNextFragment(DownloadWizardDetailsFragment())
        } catch (e: IllegalArgumentException) {
            AlertDialog.Builder(requireContext()).apply {
                setTitle(R.string.profile_download_incorrect_lpa_string)
                setMessage(R.string.profile_download_incorrect_lpa_string_message)
                setCancelable(true)
                setNegativeButton(android.R.string.cancel, null)
                show()
            }
        }
    }

    private inner class DownloadMethodViewHolder(private val root: View) : ViewHolder(root) {
        private val icon = root.requireViewById<ImageView>(R.id.download_method_icon)
        private val title = root.requireViewById<TextView>(R.id.download_method_title)

        fun bind(item: DownloadMethod) {
            icon.setImageResource(item.iconRes)
            title.setText(item.titleRes)
            root.setOnClickListener {
                // If the user elected to use another download method, reset the confirmation code flag
                // too
                state.confirmationCodeRequired = false
                item.onClick()
            }
        }
    }

    private inner class DownloadMethodAdapter : RecyclerView.Adapter<DownloadMethodViewHolder>() {
        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): DownloadMethodViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.download_method_item, parent, false)
            return DownloadMethodViewHolder(view)
        }

        override fun getItemCount(): Int = downloadMethods.size

        override fun onBindViewHolder(holder: DownloadMethodViewHolder, position: Int) {
            holder.bind(downloadMethods[position])
        }

    }
}