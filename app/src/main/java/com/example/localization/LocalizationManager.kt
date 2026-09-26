package com.example.localization

import java.util.Locale

/**
 * Localization keys representing user-facing strings across all game systems.
 */
enum class StringKey {
    // App & System
    APP_NAME,
    LOADING_ASSETS,
    CLEANING_MEMORY,
    INITIALIZING_SCENE,
    SYSTEM_READY,
    MEMORY_FREE_MB,

    // Navigation & Menus
    MENU_PLAY,
    MENU_TRAINING,
    MENU_CHARACTERS,
    MENU_STAGES,
    MENU_SETTINGS,
    MENU_RECORDS,
    MENU_QUIT,
    BACK,
    CONFIRM,
    SELECT,

    // Match & Game Modes
    MODE_STOCK_BRAWL,
    MODE_TIME_ATTACK,
    MODE_TRAINING,
    STOCKS_REMAINING,
    TIME_LEFT,
    MATCH_PAUSED,
    RESUME,
    RESTART,
    QUIT_TO_MENU,
    VICTORY,
    DEFEAT,
    MATCH_STATS,
    TOTAL_DAMAGE_DEALT,
    TOTAL_KOS,
    MATCH_DURATION,
    REMATCH,

    // Characters
    CHAR_VALKYRIE,
    CHAR_VALKYRIE_DESC,
    CHAR_CYBER_MONK,
    CHAR_CYBER_MONK_DESC,
    CHAR_SHADOW_NINJA,
    CHAR_SHADOW_NINJA_DESC,
    STAT_SPEED,
    STAT_POWER,
    STAT_DEFENSE,
    STAT_JUMP,

    // Stages
    STAGE_MAMMOTH_FORTRESS,
    STAGE_CRYSTAL_SPIRE,
    STAGE_THUNDER_PLATEAU,

    // Settings & Save
    SETTINGS_TITLE,
    SETTINGS_AUDIO,
    SETTINGS_SFX,
    SETTINGS_MUSIC,
    SETTINGS_LANGUAGE,
    SETTINGS_VIBRATION,
    SETTINGS_DATA_MANAGEMENT,
    SAVE_STATUS_SECURE,
    SAVE_RESET_BUTTON,
    SAVE_RESET_CONFIRM,
    SAVE_SUCCESS,
    SAVE_ERROR
}

/**
 * Dynamic localized language codes.
 */
enum class SupportedLanguage(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    SPANISH("es", "Español"),
    JAPANESE("ja", "日本語"),
    FRENCH("fr", "Français"),
    GERMAN("de", "Deutsch"),
    PORTUGUESE("pt", "Português")
}

/**
 * Event-driven dynamic localization manager.
 * Reads translation matrices based on system locale or runtime user preference override.
 */
class LocalizationManager private constructor() {

    private var currentLanguage: SupportedLanguage = detectDeviceLanguage()
    private val listeners = mutableListOf<(SupportedLanguage) -> Unit>()

    private val translations: Map<SupportedLanguage, Map<StringKey, String>> = mapOf(
        SupportedLanguage.ENGLISH to mapOf(
            StringKey.APP_NAME to "Brawl Arena",
            StringKey.LOADING_ASSETS to "Loading Arena Assets...",
            StringKey.CLEANING_MEMORY to "Executing Zero-Leak Garbage Collection...",
            StringKey.INITIALIZING_SCENE to "Initializing Kinematic Physics Loop...",
            StringKey.SYSTEM_READY to "System Ready",
            StringKey.MEMORY_FREE_MB to "Available RAM",

            StringKey.MENU_PLAY to "PLAY BRAWL",
            StringKey.MENU_TRAINING to "TRAINING DOJO",
            StringKey.MENU_CHARACTERS to "FIGHTERS",
            StringKey.MENU_STAGES to "ARENAS",
            StringKey.MENU_SETTINGS to "SETTINGS",
            StringKey.MENU_RECORDS to "BATTLE RECORDS",
            StringKey.MENU_QUIT to "EXIT",
            StringKey.BACK to "BACK",
            StringKey.CONFIRM to "CONFIRM",
            StringKey.SELECT to "SELECT",

            StringKey.MODE_STOCK_BRAWL to "Stock Brawl (3 Lives)",
            StringKey.MODE_TIME_ATTACK to "Time Battle (2 Mins)",
            StringKey.MODE_TRAINING to "Free Training",
            StringKey.STOCKS_REMAINING to "Stocks",
            StringKey.TIME_LEFT to "Time",
            StringKey.MATCH_PAUSED to "BATTLE PAUSED",
            StringKey.RESUME to "RESUME",
            StringKey.RESTART to "RESTART MATCH",
            StringKey.QUIT_TO_MENU to "MAIN MENU",
            StringKey.VICTORY to "VICTORY!",
            StringKey.DEFEAT to "RING OUT / DEFEAT",
            StringKey.MATCH_STATS to "Post-Match Telemetry",
            StringKey.TOTAL_DAMAGE_DEALT to "Damage Inflicted",
            StringKey.TOTAL_KOS to "Opponents Knocked Out",
            StringKey.MATCH_DURATION to "Battle Duration",
            StringKey.REMATCH to "REMATCH",

            StringKey.CHAR_VALKYRIE to "Brynhild the Valkyrie",
            StringKey.CHAR_VALKYRIE_DESC to "Balanced high-mobility aerial duelist with twin spears.",
            StringKey.CHAR_CYBER_MONK to "Kaelen Cyber-Monk",
            StringKey.CHAR_CYBER_MONK_DESC to "Heavy bruiser with explosive plasma shockwave gauntlets.",
            StringKey.CHAR_SHADOW_NINJA to "Hayate Shadow-Blade",
            StringKey.CHAR_SHADOW_NINJA_DESC to "High-speed assassin with rapid dash-cancels and wall climbs.",
            StringKey.STAT_SPEED to "Speed",
            StringKey.STAT_POWER to "Power",
            StringKey.STAT_DEFENSE to "Defense",
            StringKey.STAT_JUMP to "Jump",

            StringKey.STAGE_MAMMOTH_FORTRESS to "Mammoth Fortress",
            StringKey.STAGE_CRYSTAL_SPIRE to "Crystal Spire",
            StringKey.STAGE_THUNDER_PLATEAU to "Thunder Plateau",

            StringKey.SETTINGS_TITLE to "Global System Configuration",
            StringKey.SETTINGS_AUDIO to "Audio Channels",
            StringKey.SETTINGS_SFX to "SFX Volume",
            StringKey.SETTINGS_MUSIC to "Music Volume",
            StringKey.SETTINGS_LANGUAGE to "Game Language",
            StringKey.SETTINGS_VIBRATION to "Haptic Feedback",
            StringKey.SETTINGS_DATA_MANAGEMENT to "Tamper-Proof Save Management",
            StringKey.SAVE_STATUS_SECURE to "AES-256 GCM Encrypted & Integrity Verified",
            StringKey.SAVE_RESET_BUTTON to "Reset Save Game",
            StringKey.SAVE_RESET_CONFIRM to "Save reset to factory default.",
            StringKey.SAVE_SUCCESS to "Save file synchronized atomically.",
            StringKey.SAVE_ERROR to "Save verification error detected!"
        ),
        SupportedLanguage.SPANISH to mapOf(
            StringKey.APP_NAME to "Brawl Arena",
            StringKey.LOADING_ASSETS to "Cargando Recursos de la Arena...",
            StringKey.CLEANING_MEMORY to "Ejecutando Recolección de Basura de Memoria...",
            StringKey.INITIALIZING_SCENE to "Iniciando Bucle Cinemático...",
            StringKey.SYSTEM_READY to "Sistema Listo",
            StringKey.MEMORY_FREE_MB to "RAM Libre",

            StringKey.MENU_PLAY to "JUGAR COMBATE",
            StringKey.MENU_TRAINING to "DOJO DE ENTRENAMIENTO",
            StringKey.MENU_CHARACTERS to "LUCHADORES",
            StringKey.MENU_STAGES to "ARENAS",
            StringKey.MENU_SETTINGS to "AJUSTES",
            StringKey.MENU_RECORDS to "REGISTROS",
            StringKey.MENU_QUIT to "SALIR",
            StringKey.BACK to "ATRÁS",
            StringKey.CONFIRM to "CONFIRMAR",
            StringKey.SELECT to "SELECCIONAR",

            StringKey.MODE_STOCK_BRAWL to "Combate por Vidas (3 Vidas)",
            StringKey.MODE_TIME_ATTACK to "Batalla por Tiempo (2 Min)",
            StringKey.MODE_TRAINING to "Entrenamiento Libre",
            StringKey.STOCKS_REMAINING to "Vidas",
            StringKey.TIME_LEFT to "Tiempo",
            StringKey.MATCH_PAUSED to "BATALLA EN PAUSA",
            StringKey.RESUME to "CONTINUAR",
            StringKey.RESTART to "REINICIAR COMBATE",
            StringKey.QUIT_TO_MENU to "MENÚ PRINCIPAL",
            StringKey.VICTORY to "¡VICTORIA!",
            StringKey.DEFEAT to "DERROTA",
            StringKey.MATCH_STATS to "Telemetría Posterior",
            StringKey.TOTAL_DAMAGE_DEALT to "Daño Infligido",
            StringKey.TOTAL_KOS to "Rivales Noqueados",
            StringKey.MATCH_DURATION to "Duración",
            StringKey.REMATCH to "REVANCHA",

            StringKey.CHAR_VALKYRIE to "Brynhild la Valquiria",
            StringKey.CHAR_VALKYRIE_DESC to "Duelista aérea equilibrada de gran movilidad.",
            StringKey.CHAR_CYBER_MONK to "Kaelen Monje Cibernético",
            StringKey.CHAR_CYBER_MONK_DESC to "Luchador pesado con guanteletes de plasma explosivo.",
            StringKey.CHAR_SHADOW_NINJA to "Hayate Filo de Sombra",
            StringKey.CHAR_SHADOW_NINJA_DESC to "Asesino ultrarrápido con cancelaciones de esquiva.",
            StringKey.STAT_SPEED to "Velocidad",
            StringKey.STAT_POWER to "Poder",
            StringKey.STAT_DEFENSE to "Defensa",
            StringKey.STAT_JUMP to "Salto",

            StringKey.STAGE_MAMMOTH_FORTRESS to "Fortaleza Mamut",
            StringKey.STAGE_CRYSTAL_SPIRE to "Aguja de Cristal",
            StringKey.STAGE_THUNDER_PLATEAU to "Meseta de Truenos",

            StringKey.SETTINGS_TITLE to "Configuración Global",
            StringKey.SETTINGS_AUDIO to "Canales de Audio",
            StringKey.SETTINGS_SFX to "Volumen de Efectos",
            StringKey.SETTINGS_MUSIC to "Volumen de Música",
            StringKey.SETTINGS_LANGUAGE to "Idioma",
            StringKey.SETTINGS_VIBRATION to "Vibración Háptica",
            StringKey.SETTINGS_DATA_MANAGEMENT to "Gestión de Datos Segura",
            StringKey.SAVE_STATUS_SECURE to "Cifrado AES-256 GCM e Integridad Verificada",
            StringKey.SAVE_RESET_BUTTON to "Reiniciar Datos",
            StringKey.SAVE_RESET_CONFIRM to "Datos restaurados con éxito.",
            StringKey.SAVE_SUCCESS to "Guardado atómico completado.",
            StringKey.SAVE_ERROR to "¡Error de verificación en guardado!"
        ),
        SupportedLanguage.JAPANESE to mapOf(
            StringKey.APP_NAME to "ブロウル・アリーナ",
            StringKey.LOADING_ASSETS to "アリーナアセット読み込み中...",
            StringKey.CLEANING_MEMORY to "メモリGCクリーンアップ実行中...",
            StringKey.INITIALIZING_SCENE to "物理演算ループ初期化中...",
            StringKey.SYSTEM_READY to "システム準備完了",
            StringKey.MEMORY_FREE_MB to "空きRAM容量",

            StringKey.MENU_PLAY to "対戦開始",
            StringKey.MENU_TRAINING to "トレーニング道場",
            StringKey.MENU_CHARACTERS to "ファイター一覧",
            StringKey.MENU_STAGES to "ステージ選択",
            StringKey.MENU_SETTINGS to "設定",
            StringKey.MENU_RECORDS to "戦績記録",
            StringKey.MENU_QUIT to "終了",
            StringKey.BACK to "戻る",
            StringKey.CONFIRM to "決定",
            StringKey.SELECT to "選択",

            StringKey.MODE_STOCK_BRAWL to "ストック戦 (残機3)",
            StringKey.MODE_TIME_ATTACK to "タイム戦 (2分)",
            StringKey.MODE_TRAINING to "フリー練習",
            StringKey.STOCKS_REMAINING to "残機",
            StringKey.TIME_LEFT to "残り時間",
            StringKey.MATCH_PAUSED to "ポーズ中",
            StringKey.RESUME to "再開",
            StringKey.RESTART to "リスタート",
            StringKey.QUIT_TO_MENU to "タイトルへ戻る",
            StringKey.VICTORY to "勝利！",
            StringKey.DEFEAT to "敗北",
            StringKey.MATCH_STATS to "対戦リザルト",
            StringKey.TOTAL_DAMAGE_DEALT to "与えたダメージ",
            StringKey.TOTAL_KOS to "撃墜数",
            StringKey.MATCH_DURATION to "試合時間",
            StringKey.REMATCH to "再戦",

            StringKey.CHAR_VALKYRIE to "戦乙女ブリュンヒルド",
            StringKey.CHAR_VALKYRIE_DESC to "空中戦を得意とするバランス型槍戦士。",
            StringKey.CHAR_CYBER_MONK to "電脳僧侶ケーレン",
            StringKey.CHAR_CYBER_MONK_DESC to "プラズマ手甲で圧倒的威力を誇る重装拳士。",
            StringKey.CHAR_SHADOW_NINJA to "影刃のハヤテ",
            StringKey.CHAR_SHADOW_NINJA_DESC to "超高速ダッシュキャンセルを操る暗殺忍者。",
            StringKey.STAT_SPEED to "スピード",
            StringKey.STAT_POWER to "パワー",
            StringKey.STAT_DEFENSE to "防御力",
            StringKey.STAT_JUMP to "跳躍力",

            StringKey.STAGE_MAMMOTH_FORTRESS to "マンモス要塞",
            StringKey.STAGE_CRYSTAL_SPIRE to "水晶の尖塔",
            StringKey.STAGE_THUNDER_PLATEAU to "雷鳴の台地",

            StringKey.SETTINGS_TITLE to "システム設定",
            StringKey.SETTINGS_AUDIO to "オーディオ設定",
            StringKey.SETTINGS_SFX to "効果音音量",
            StringKey.SETTINGS_MUSIC to "BGM音量",
            StringKey.SETTINGS_LANGUAGE to "表示言語",
            StringKey.SETTINGS_VIBRATION to "振動フィードバック",
            StringKey.SETTINGS_DATA_MANAGEMENT to "データ保全管理",
            StringKey.SAVE_STATUS_SECURE to "AES-256 GCM暗号化・改ざん検証済み",
            StringKey.SAVE_RESET_BUTTON to "セーブデータ初期化",
            StringKey.SAVE_RESET_CONFIRM to "セーブデータを初期化しました。",
            StringKey.SAVE_SUCCESS to "アトミックセーブ完了。",
            StringKey.SAVE_ERROR to "セーブ改ざん検知エラー！"
        ),
        SupportedLanguage.FRENCH to mapOf(
            StringKey.APP_NAME to "Brawl Arena",
            StringKey.LOADING_ASSETS to "Chargement des Ressources...",
            StringKey.CLEANING_MEMORY to "Nettoyage Mémoire Anti-Fuite...",
            StringKey.INITIALIZING_SCENE to "Initialisation du Moteur Physique...",
            StringKey.SYSTEM_READY to "Système Prêt",
            StringKey.MEMORY_FREE_MB to "RAM Libre",

            StringKey.MENU_PLAY to "COMBAT",
            StringKey.MENU_TRAINING to "ENTRAÎNEMENT",
            StringKey.MENU_CHARACTERS to "COMBATTANTS",
            StringKey.MENU_STAGES to "ARÈNES",
            StringKey.MENU_SETTINGS to "OPTIONS",
            StringKey.MENU_RECORDS to "HISTORIQUE",
            StringKey.MENU_QUIT to "QUITTER",
            StringKey.BACK to "RETOUR",
            StringKey.CONFIRM to "CONFIRMER",
            StringKey.SELECT to "CHOISIR",

            StringKey.MODE_STOCK_BRAWL to "Combat par Vies (3 Vies)",
            StringKey.MODE_TIME_ATTACK to "Bataille Chronométrée (2 Min)",
            StringKey.MODE_TRAINING to "Entraînement Libre",
            StringKey.STOCKS_REMAINING to "Vies",
            StringKey.TIME_LEFT to "Temps",
            StringKey.MATCH_PAUSED to "PARTIE EN PAUSE",
            StringKey.RESUME to "REPRENDRE",
            StringKey.RESTART to "RECOMMENCER",
            StringKey.QUIT_TO_MENU to "MENU PRINCIPAL",
            StringKey.VICTORY to "VICTOIRE !",
            StringKey.DEFEAT to "DÉFAITE",
            StringKey.MATCH_STATS to "Télémétrie du Match",
            StringKey.TOTAL_DAMAGE_DEALT to "Dégâts Infligés",
            StringKey.TOTAL_KOS to "Éjections",
            StringKey.MATCH_DURATION to "Durée du Match",
            StringKey.REMATCH to "REVANCHE",

            StringKey.CHAR_VALKYRIE to "Brynhild la Valkyrie",
            StringKey.CHAR_VALKYRIE_DESC to "Duelliste aérienne agile aux lances jumelles.",
            StringKey.CHAR_CYBER_MONK to "Kaelen Moine-Cyber",
            StringKey.CHAR_CYBER_MONK_DESC to "Cogneur lourd aux gantelets plasma dévastateurs.",
            StringKey.CHAR_SHADOW_NINJA to "Hayate Lame d'Ombre",
            StringKey.CHAR_SHADOW_NINJA_DESC to "Assassin rapide expert en ruées éclairs.",
            StringKey.STAT_SPEED to "Vitesse",
            StringKey.STAT_POWER to "Force",
            StringKey.STAT_DEFENSE to "Défense",
            StringKey.STAT_JUMP to "Saut",

            StringKey.STAGE_MAMMOTH_FORTRESS to "Forteresse Mammouth",
            StringKey.STAGE_CRYSTAL_SPIRE to "Flèche de Cristal",
            StringKey.STAGE_THUNDER_PLATEAU to "Plateau du Tonnerre",

            StringKey.SETTINGS_TITLE to "Configuration Système",
            StringKey.SETTINGS_AUDIO to "Canaux Audio",
            StringKey.SETTINGS_SFX to "Volume Effets",
            StringKey.SETTINGS_MUSIC to "Volume Musique",
            StringKey.SETTINGS_LANGUAGE to "Langue",
            StringKey.SETTINGS_VIBRATION to "Retours Haptiques",
            StringKey.SETTINGS_DATA_MANAGEMENT to "Sauvegarde Sécurisée",
            StringKey.SAVE_STATUS_SECURE to "Cryptage AES-256 GCM Vérifié",
            StringKey.SAVE_RESET_BUTTON to "Réinitialiser Données",
            StringKey.SAVE_RESET_CONFIRM to "Sauvegarde réinitialisée.",
            StringKey.SAVE_SUCCESS to "Données enregistrées avec succès.",
            StringKey.SAVE_ERROR to "Erreur d'intégrité de la sauvegarde !"
        ),
        SupportedLanguage.GERMAN to mapOf(
            StringKey.APP_NAME to "Brawl Arena",
            StringKey.LOADING_ASSETS to "Lade Arena-Ressourcen...",
            StringKey.CLEANING_MEMORY to "Speicherbereinigung läuft...",
            StringKey.INITIALIZING_SCENE to "Physik-Engine wird initialisiert...",
            StringKey.SYSTEM_READY to "System Bereit",
            StringKey.MEMORY_FREE_MB to "Freier RAM",

            StringKey.MENU_PLAY to "KAMPF STARTEN",
            StringKey.MENU_TRAINING to "TRAININGS-DOJO",
            StringKey.MENU_CHARACTERS to "KÄMPFER",
            StringKey.MENU_STAGES to "ARENEN",
            StringKey.MENU_SETTINGS to "EINSTELLUNGEN",
            StringKey.MENU_RECORDS to "KAMPFAUFZEICHNUNG",
            StringKey.MENU_QUIT to "BEENDEN",
            StringKey.BACK to "ZURÜCK",
            StringKey.CONFIRM to "BESTÄTIGEN",
            StringKey.SELECT to "WÄHLEN",

            StringKey.MODE_STOCK_BRAWL to "Leben-Kampf (3 Leben)",
            StringKey.MODE_TIME_ATTACK to "Zeitkampf (2 Min)",
            StringKey.MODE_TRAINING to "Freies Training",
            StringKey.STOCKS_REMAINING to "Leben",
            StringKey.TIME_LEFT to "Zeit",
            StringKey.MATCH_PAUSED to "KAMPF PAUSIERT",
            StringKey.RESUME to "WEITER",
            StringKey.RESTART to "NEUSTART",
            StringKey.QUIT_TO_MENU to "HAUPTMENÜ",
            StringKey.VICTORY to "SIEG!",
            StringKey.DEFEAT to "NIEDERLAGE",
            StringKey.MATCH_STATS to "Kampf-Telemetrie",
            StringKey.TOTAL_DAMAGE_DEALT to "Ausgeteilter Schaden",
            StringKey.TOTAL_KOS to "Besiegte Gegner",
            StringKey.MATCH_DURATION to "Kampfdauer",
            StringKey.REMATCH to "REVANCHE",

            StringKey.CHAR_VALKYRIE to "Brynhild die Walküre",
            StringKey.CHAR_VALKYRIE_DESC to "Ausgewogene Luftduellantin mit Zwillingsspeeren.",
            StringKey.CHAR_CYBER_MONK to "Kaelen Cyber-Mönch",
            StringKey.CHAR_CYBER_MONK_DESC to "Schwerer Kämpfer mit explosiven Plasmahandschuhen.",
            StringKey.CHAR_SHADOW_NINJA to "Hayate Schattenklinge",
            StringKey.CHAR_SHADOW_NINJA_DESC to "Blitzschneller Assassine mit Dash-Abbrüchen.",
            StringKey.STAT_SPEED to "Geschwindigkeit",
            StringKey.STAT_POWER to "Stärke",
            StringKey.STAT_DEFENSE to "Verteidigung",
            StringKey.STAT_JUMP to "Sprungkraft",

            StringKey.STAGE_MAMMOTH_FORTRESS to "Mammut-Festung",
            StringKey.STAGE_CRYSTAL_SPIRE to "Kristallturm",
            StringKey.STAGE_THUNDER_PLATEAU to "Donnerplateau",

            StringKey.SETTINGS_TITLE to "Systemkonfiguration",
            StringKey.SETTINGS_AUDIO to "Audio-Kanäle",
            StringKey.SETTINGS_SFX to "SFX-Lautstärke",
            StringKey.SETTINGS_MUSIC to "Musik-Lautstärke",
            StringKey.SETTINGS_LANGUAGE to "Sprache",
            StringKey.SETTINGS_VIBRATION to "Haptisches Feedback",
            StringKey.SETTINGS_DATA_MANAGEMENT to "Manipulationssichere Speicherung",
            StringKey.SAVE_STATUS_SECURE to "AES-256 GCM verschlüsselt und verifiziert",
            StringKey.SAVE_RESET_BUTTON to "Speicherstand zurücksetzen",
            StringKey.SAVE_RESET_CONFIRM to "Speicherstand zurückgesetzt.",
            StringKey.SAVE_SUCCESS to "Atomar gespeichert.",
            StringKey.SAVE_ERROR to "Integritätsfehler im Speicherstand!"
        ),
        SupportedLanguage.PORTUGUESE to mapOf(
            StringKey.APP_NAME to "Brawl Arena",
            StringKey.LOADING_ASSETS to "Carregando Recursos da Arena...",
            StringKey.CLEANING_MEMORY to "Executando Coleta de Lixo na Memória...",
            StringKey.INITIALIZING_SCENE to "Inicializando Motor Físico...",
            StringKey.SYSTEM_READY to "Sistema Pronto",
            StringKey.MEMORY_FREE_MB to "RAM Disponível",

            StringKey.MENU_PLAY to "JOGAR",
            StringKey.MENU_TRAINING to "DOJO DE TREINO",
            StringKey.MENU_CHARACTERS to "LUTADORES",
            StringKey.MENU_STAGES to "ARENAS",
            StringKey.MENU_SETTINGS to "CONFIGURAÇÕES",
            StringKey.MENU_RECORDS to "REGISTROS",
            StringKey.MENU_QUIT to "SAIR",
            StringKey.BACK to "VOLTAR",
            StringKey.CONFIRM to "CONFIRMAR",
            StringKey.SELECT to "SELECIONAR",

            StringKey.MODE_STOCK_BRAWL to "Batalha por Vidas (3 Vidas)",
            StringKey.MODE_TIME_ATTACK to "Batalha por Tempo (2 Min)",
            StringKey.MODE_TRAINING to "Treino Livre",
            StringKey.STOCKS_REMAINING to "Vidas",
            StringKey.TIME_LEFT to "Tempo",
            StringKey.MATCH_PAUSED to "BATALHA PAUSADA",
            StringKey.RESUME to "CONTINUAR",
            StringKey.RESTART to "REINICIAR",
            StringKey.QUIT_TO_MENU to "MENU PRINCIPAL",
            StringKey.VICTORY to "VITÓRIA!",
            StringKey.DEFEAT to "DERROTA",
            StringKey.MATCH_STATS to "Telemetria da Partida",
            StringKey.TOTAL_DAMAGE_DEALT to "Dano Causado",
            StringKey.TOTAL_KOS to "Nautes",
            StringKey.MATCH_DURATION to "Duração",
            StringKey.REMATCH to "REVANCHE",

            StringKey.CHAR_VALKYRIE to "Brynhild a Valquíria",
            StringKey.CHAR_VALKYRIE_DESC to "Duelista aérea veloz com lanças gêmeas.",
            StringKey.CHAR_CYBER_MONK to "Kaelen Monge Cibernético",
            StringKey.CHAR_CYBER_MONK_DESC to "Lutador com manoplas de plasma explosivas.",
            StringKey.CHAR_SHADOW_NINJA to "Hayate Lâmina Sombria",
            StringKey.CHAR_SHADOW_NINJA_DESC to "Assassino ultrarrápido com cancelamento de investida.",
            StringKey.STAT_SPEED to "Velocidade",
            StringKey.STAT_POWER to "Poder",
            StringKey.STAT_DEFENSE to "Defesa",
            StringKey.STAT_JUMP to "Pulo",

            StringKey.STAGE_MAMMOTH_FORTRESS to "Fortaleza Mamute",
            StringKey.STAGE_CRYSTAL_SPIRE to "Pináculo de Cristal",
            StringKey.STAGE_THUNDER_PLATEAU to "Planalto do Trovão",

            StringKey.SETTINGS_TITLE to "Configurações do Sistema",
            StringKey.SETTINGS_AUDIO to "Canais de Áudio",
            StringKey.SETTINGS_SFX to "Volume dos Efeitos",
            StringKey.SETTINGS_MUSIC to "Volume da Música",
            StringKey.SETTINGS_LANGUAGE to "Idioma",
            StringKey.SETTINGS_VIBRATION to "Retorno Háptico",
            StringKey.SETTINGS_DATA_MANAGEMENT to "Armazenamento Seguro",
            StringKey.SAVE_STATUS_SECURE to "AES-256 GCM Criptografado e Verificado",
            StringKey.SAVE_RESET_BUTTON to "Resetar Dados",
            StringKey.SAVE_RESET_CONFIRM to "Dados resetados com sucesso.",
            StringKey.SAVE_SUCCESS to "Salvo atomicamente.",
            StringKey.SAVE_ERROR to "Erro na verificação do arquivo!"
        )
    )

    fun getString(key: StringKey): String {
        return translations[currentLanguage]?.get(key)
            ?: translations[SupportedLanguage.ENGLISH]?.get(key)
            ?: key.name
    }

    fun setLanguage(lang: SupportedLanguage) {
        if (currentLanguage != lang) {
            currentLanguage = lang
            notifyListeners()
        }
    }

    fun getLanguage(): SupportedLanguage = currentLanguage

    fun addListener(listener: (SupportedLanguage) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (SupportedLanguage) -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        for (i in listeners.indices) {
            listeners[i](currentLanguage)
        }
    }

    private fun detectDeviceLanguage(): SupportedLanguage {
        val iso = Locale.getDefault().language.lowercase()
        return when {
            iso.startsWith("es") -> SupportedLanguage.SPANISH
            iso.startsWith("ja") -> SupportedLanguage.JAPANESE
            iso.startsWith("fr") -> SupportedLanguage.FRENCH
            iso.startsWith("de") -> SupportedLanguage.GERMAN
            iso.startsWith("pt") -> SupportedLanguage.PORTUGUESE
            else -> SupportedLanguage.ENGLISH
        }
    }

    companion object {
        val instance: LocalizationManager by lazy { LocalizationManager() }
    }
}
