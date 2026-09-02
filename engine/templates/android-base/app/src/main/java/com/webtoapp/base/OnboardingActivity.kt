package com.webtoapp.base

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.webtoapp.base.databinding.ActivityOnboardingBinding
import com.webtoapp.base.databinding.ItemOnboardingSlideBinding
import org.json.JSONObject

data class OnboardingSlide(
    val title: String,
    val description: String,
    val icon: String = "Home",
    val imageBase64: String? = null
)

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private val slides = mutableListOf<OnboardingSlide>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadSlides()

        val adapter = OnboardingAdapter(slides)
        binding.viewPager.adapter = adapter

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                if (position == slides.size - 1) {
                    binding.btnNext.text = "Get Started"
                } else {
                    binding.btnNext.text = "Next"
                }
            }
        })

        binding.btnNext.setOnClickListener {
            val current = binding.viewPager.currentItem
            if (current < slides.size - 1) {
                binding.viewPager.currentItem = current + 1
            } else {
                finishOnboarding()
            }
        }

        binding.btnSkip.setOnClickListener {
            finishOnboarding()
        }
    }

    private fun getIconResourceForName(iconName: String): Int {
        return when (iconName.lowercase()) {
            "home", "house" -> android.R.drawable.ic_menu_today
            "shop", "shoppingbag", "shoppingcart", "cart", "store", "tag", "gift", "package", "creditcard", "wallet", "dollarsign", "percent" -> android.R.drawable.ic_menu_agenda
            "search" -> android.R.drawable.ic_menu_search
            "grid", "layers", "list", "categories", "filetext", "bookopen" -> android.R.drawable.ic_menu_view
            "user", "account", "profile", "users" -> android.R.drawable.ic_menu_myplaces
            "phone", "contact", "call" -> android.R.drawable.ic_menu_call
            "bell", "notifications" -> android.R.drawable.ic_popup_reminder
            "mail", "send", "messagecircle", "messagesquare", "chat" -> android.R.drawable.ic_dialog_email
            "help", "helpcircle", "info" -> android.R.drawable.ic_menu_help
            "camera" -> android.R.drawable.ic_menu_camera
            "video", "tv" -> android.R.drawable.presence_video_online
            "heart", "star", "award", "bookmark", "sparkles", "thumbsup" -> android.R.drawable.btn_star_big_on
            "map", "mappin", "navigation", "compass", "globe" -> android.R.drawable.ic_menu_mapmode
            "settings" -> android.R.drawable.ic_menu_preferences
            "share", "share2" -> android.R.drawable.ic_menu_share
            "calendar", "clock" -> android.R.drawable.ic_menu_recent_history
            else -> android.R.drawable.btn_star_big_on
        }
    }

    private fun loadSlides() {
        try {
            val jsonString = assets.open("app_config.json").bufferedReader().use { it.readText() }
            val json = JSONObject(jsonString)
            if (json.has("onboardingSlides")) {
                val array = json.getJSONArray("onboardingSlides")
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    slides.add(
                        OnboardingSlide(
                            title = item.optString("title", "Welcome"),
                            description = item.optString("description", ""),
                            icon = item.optString("icon", "Home"),
                            imageBase64 = if (item.has("image") && item.getString("image").isNotBlank()) item.getString("image") else null
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (slides.isEmpty()) {
            slides.add(OnboardingSlide("Welcome to the App", "Browse and interact with our live website instantly.", "Home"))
            slides.add(OnboardingSlide("Fast & Native", "Enjoy hardware-accelerated performance, push alerts and offline access.", "Sparkles"))
            slides.add(OnboardingSlide("Stay Connected", "Get instant updates and access all features anywhere.", "Bell"))
        }
    }

    private fun finishOnboarding() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        prefs.edit().putBoolean("has_seen_onboarding", true).apply()

        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    inner class OnboardingAdapter(private val list: List<OnboardingSlide>) :
        RecyclerView.Adapter<OnboardingAdapter.SlideViewHolder>() {

        inner class SlideViewHolder(val itemBinding: ItemOnboardingSlideBinding) :
            RecyclerView.ViewHolder(itemBinding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlideViewHolder {
            val viewBinding = ItemOnboardingSlideBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return SlideViewHolder(viewBinding)
        }

        override fun onBindViewHolder(holder: SlideViewHolder, position: Int) {
            val item = list[position]
            holder.itemBinding.slideTitle.text = item.title
            holder.itemBinding.slideDesc.text = item.description

            if (!item.imageBase64.isNullOrBlank()) {
                try {
                    val pureBase64 = if (item.imageBase64.contains(",")) item.imageBase64.substringAfter(",") else item.imageBase64
                    val decodedBytes = Base64.decode(pureBase64, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                    if (bitmap != null) {
                        holder.itemBinding.slideIcon.setImageBitmap(bitmap)
                    } else {
                        holder.itemBinding.slideIcon.setImageResource(getIconResourceForName(item.icon))
                    }
                } catch (e: Exception) {
                    holder.itemBinding.slideIcon.setImageResource(getIconResourceForName(item.icon))
                }
            } else {
                holder.itemBinding.slideIcon.setImageResource(getIconResourceForName(item.icon))
            }
        }

        override fun getItemCount(): Int = list.size
    }
}
