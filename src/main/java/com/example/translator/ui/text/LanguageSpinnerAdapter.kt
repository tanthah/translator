package com.example.translator.ui.text

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import com.example.translator.R
import com.example.translator.data.model.Language

class LanguageSpinnerAdapter(
    private val context: Context,
    private val languages: List<Language>
) : BaseAdapter() {

    override fun getCount(): Int = languages.size

    override fun getItem(position: Int): LanguageSpinnerItem = LanguageSpinnerItem(languages[position])

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_language_spinner, parent, false)
        val language = languages[position]

        val tvLanguageName = view.findViewById<TextView>(R.id.tv_language_name)
        val tvNativeName = view.findViewById<TextView>(R.id.tv_native_name)

        tvLanguageName.text = language.languageName
        tvNativeName.text = language.nativeName

        return view
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup?): View {
        return getView(position, convertView, parent)
    }
}

data class LanguageSpinnerItem(val language: Language) {
    override fun toString(): String = language.languageName
}