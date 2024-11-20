package im.angry.openeuicc.ui.wizard

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import im.angry.openeuicc.common.R
import im.angry.openeuicc.ui.BaseEuiccAccessActivity
import im.angry.openeuicc.util.*

class DownloadWizardActivity: BaseEuiccAccessActivity() {
    data class DownloadWizardState(
        var selectedLogicalSlot: Int,
        var smdp: String,
        var matchingId: String,
        var confirmationCode: String,
        var imei: String,
    )

    private lateinit var state: DownloadWizardState

    private lateinit var progressBar: ProgressBar
    private lateinit var nextButton: Button
    private lateinit var prevButton: Button

    private var currentFragment: DownloadWizardStepFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_download_wizard)
        onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Make back == prev
                onPrevPressed()
            }
        })

        state = DownloadWizardState(
            intent.getIntExtra("selectedLogicalSlot", 0),
            "",
            "",
            "",
            ""
        )

        progressBar = requireViewById(R.id.progress)
        nextButton = requireViewById(R.id.download_wizard_next)
        prevButton = requireViewById(R.id.download_wizard_back)

        nextButton.setOnClickListener {
            onNextPressed()
        }

        prevButton.setOnClickListener {
            onPrevPressed()
        }

        val navigation = requireViewById<View>(R.id.download_wizard_navigation)
        val origHeight = navigation.layoutParams.height

        ViewCompat.setOnApplyWindowInsetsListener(navigation) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(bars.left, 0, bars.right, bars.bottom)
            val newParams = navigation.layoutParams
            newParams.height = origHeight + bars.bottom
            navigation.layoutParams = newParams
            WindowInsetsCompat.CONSUMED
        }

        val fragmentRoot = requireViewById<View>(R.id.step_fragment_container)
        ViewCompat.setOnApplyWindowInsetsListener(fragmentRoot) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(bars.left, bars.top, bars.right, 0)
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun onPrevPressed() {
        if (currentFragment?.hasPrev == true) {
            val prevFrag = currentFragment?.createPrevFragment()
            if (prevFrag == null) {
                finish()
            } else {
                showFragment(prevFrag, R.anim.slide_in_left, R.anim.slide_out_right)
            }
        }
    }

    private fun onNextPressed() {
        if (currentFragment?.hasNext == true) {
            val nextFrag = currentFragment?.createNextFragment()
            if (nextFrag == null) {
                finish()
            } else {
                showFragment(nextFrag, R.anim.slide_in_right, R.anim.slide_out_left)
            }
        }
    }

    override fun onInit() {
        progressBar.visibility = View.GONE
        showFragment(DownloadWizardSlotSelectFragment())
    }

    private fun showFragment(
        nextFrag: DownloadWizardStepFragment,
        enterAnim: Int = 0,
        exitAnim: Int = 0
    ) {
        currentFragment = nextFrag
        supportFragmentManager.beginTransaction().setCustomAnimations(enterAnim, exitAnim)
            .replace(R.id.step_fragment_container, nextFrag)
            .commit()
        refreshButtons()
    }

    private fun refreshButtons() {
        currentFragment?.let {
            nextButton.visibility = if (it.hasNext) {
                View.VISIBLE
            } else {
                View.GONE
            }
            prevButton.visibility = if (it.hasPrev) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }

    abstract class DownloadWizardStepFragment : Fragment(), OpenEuiccContextMarker {
        protected val state: DownloadWizardState
            get() = (requireActivity() as DownloadWizardActivity).state

        abstract val hasNext: Boolean
        abstract val hasPrev: Boolean
        abstract fun createNextFragment(): DownloadWizardStepFragment?
        abstract fun createPrevFragment(): DownloadWizardStepFragment?

        protected fun gotoNextFragment(next: DownloadWizardStepFragment? = null) {
            val realNext = next ?: createNextFragment()
            (requireActivity() as DownloadWizardActivity).showFragment(
                realNext!!,
                R.anim.slide_in_right,
                R.anim.slide_out_left
            )
        }

        protected fun hideProgressBar() {
            (requireActivity() as DownloadWizardActivity).progressBar.visibility = View.GONE
        }

        protected fun showProgressBar(progressValue: Int) {
            (requireActivity() as DownloadWizardActivity).progressBar.apply {
                visibility = View.VISIBLE
                if (progressValue >= 0) {
                    isIndeterminate = false
                    progress = progressValue
                } else {
                    isIndeterminate = true
                }
            }
        }

        protected fun refreshButtons() {
            (requireActivity() as DownloadWizardActivity).refreshButtons()
        }
    }
}