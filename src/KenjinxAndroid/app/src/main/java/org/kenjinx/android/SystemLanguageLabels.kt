package org.kenjinx.android

val SystemLanguage.displayName: String
    get() = when (this) {
        SystemLanguage.Japanese -> tr("Japonês")
        SystemLanguage.AmericanEnglish -> tr("Inglês (EUA)")
        SystemLanguage.French -> tr("Francês")
        SystemLanguage.German -> tr("Alemão")
        SystemLanguage.Italian -> tr("Italiano")
        SystemLanguage.Spanish -> tr("Espanhol (Espanha)")
        SystemLanguage.Chinese -> tr("Chinês")
        SystemLanguage.Korean -> tr("Coreano")
        SystemLanguage.Dutch -> tr("Holandês")
        SystemLanguage.Portuguese -> tr("Português (Portugal)")
        SystemLanguage.Russian -> tr("Russo")
        SystemLanguage.Taiwanese -> tr("Chinês (Taiwan)")
        SystemLanguage.BritishEnglish -> tr("Inglês (Reino Unido)")
        SystemLanguage.CanadianFrench -> tr("Francês (Canadá)")
        SystemLanguage.LatinAmericanSpanish -> tr("Espanhol (América Latina)")
        SystemLanguage.SimplifiedChinese -> tr("Chinês simplificado")
        SystemLanguage.TraditionalChinese -> tr("Chinês tradicional")
        SystemLanguage.BrazilianPortuguese -> tr("Português (Brasil)")
    }
