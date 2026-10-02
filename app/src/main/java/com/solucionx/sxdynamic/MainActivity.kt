package com.solucionx.sxdynamic

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import com.solucionx.sxdynamic.core.Diagnostics
import com.solucionx.sxdynamic.core.NotificationListenerRuntime
import com.solucionx.sxdynamic.core.OverlayServiceController
import com.solucionx.sxdynamic.core.PermissionSnapshot
import com.solucionx.sxdynamic.core.PermissionState
import com.solucionx.sxdynamic.core.SystemSettingsNavigator
import com.solucionx.sxdynamic.data.DynamicSettings
import com.solucionx.sxdynamic.domain.IslandContent
import com.solucionx.sxdynamic.ui.IslandPreviewView
import com.solucionx.sxdynamic.ui.UiKit
import com.solucionx.sxdynamic.ui.UiKit.dp

class MainActivity : Activity() {
    private val container get() = (application as SxDynamicApp).container
    private var rebuilding = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = UiKit.BG
        window.navigationBarColor = UiKit.BG
        render()
    }

    override fun onResume() {
        super.onResume()
        val settings = container.settingsRepository.read()
        val permissions = PermissionState.read(this)
        if (permissions.notificationAccess) {
            NotificationListenerRuntime.requestRebindIfGranted(this)
        }
        if (settings.enabled && Settings.canDrawOverlays(this)) {
            OverlayServiceController.start(this)
        }
        render()
    }

    private fun render() {
        if (rebuilding) return
        rebuilding = true
        val settings = container.settingsRepository.read()
        val permissions = PermissionState.read(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(36))
            setBackgroundColor(UiKit.BG)
        }
        root.addView(UiKit.title(this, "SX Dynamic", 28f))
        root.addView(
            UiKit.subtitle(
                this,
                "Eventos importantes, mídia e status do aparelho organizados ao redor da câmera — sem depender de servidor ou rastreamento.",
            ),
        )
        root.addView(UiKit.spacer(this, 18))
        root.addView(IslandPreviewView(this))
        root.addView(UiKit.spacer(this, 16))
        root.addView(masterPanel(settings, permissions))
        root.addView(UiKit.spacer(this, 14))
        root.addView(permissionPanel(permissions))
        root.addView(UiKit.spacer(this, 14))
        root.addView(featurePanel(settings))
        root.addView(UiKit.spacer(this, 14))
        root.addView(layoutPanel(settings))
        root.addView(UiKit.spacer(this, 14))
        root.addView(diagnosticsPanel())
        root.addView(UiKit.spacer(this, 18))
        root.addView(
            UiKit.subtitle(this, "SX Dynamic 0.1.2 · Solucionx · processamento local", 12f).apply {
                gravity = Gravity.CENTER
            },
        )

        setContentView(
            ScrollView(this).apply {
                isFillViewport = true
                setBackgroundColor(UiKit.BG)
                addView(root)
            },
        )
        rebuilding = false
    }

    private fun masterPanel(settings: DynamicSettings, permissions: PermissionSnapshot): View {
        val panel = UiKit.panel(this)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val textBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(UiKit.title(this@MainActivity, "Ilha dinâmica", 18f))
            addView(
                UiKit.subtitle(
                    this@MainActivity,
                    when {
                        !permissions.overlay -> "Libere a permissão de sobreposição para ativar"
                        !permissions.notificationAccess -> "A ilha funciona, mas não pode ler notificações até você liberar o acesso"
                        !NotificationListenerRuntime.connected -> "Acesso liberado; reconectando ao serviço de notificações"
                        else -> "Pronta para notificações e mídia"
                    },
                ),
            )
        }
        row.addView(textBox, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(
            Switch(this).apply {
                isChecked = settings.enabled
                setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
                    container.settingsRepository.setEnabled(checked)
                    if (checked) {
                        if (Settings.canDrawOverlays(this@MainActivity)) {
                            OverlayServiceController.start(this@MainActivity)
                        } else {
                            SystemSettingsNavigator.overlay(this@MainActivity)
                        }
                    } else {
                        OverlayServiceController.stop(this@MainActivity)
                    }
                }
            },
        )
        panel.addView(row)
        return panel
    }

    private fun permissionPanel(permissions: PermissionSnapshot): View {
        val panel = UiKit.panel(this)
        panel.addView(UiKit.title(this, "Acesso necessário", 18f))
        panel.addView(
            UiKit.subtitle(
                this,
                "O SX Dynamic pede somente o que precisa. Você pode revogar qualquer acesso pelo Android.",
            ),
        )
        panel.addView(UiKit.spacer(this, 12))
        addPermissionRow(panel, "Sobre outros apps", permissions.overlay, "Abrir") {
            SystemSettingsNavigator.overlay(this)
        }
        addPermissionRow(panel, "Acesso às notificações", permissions.notificationAccess, "Abrir") {
            SystemSettingsNavigator.notificationListener(this)
        }
        if (permissions.notificationAccess) {
            addPermissionRow(
                panel,
                "Listener do sistema",
                NotificationListenerRuntime.connected,
                "Reconectar",
            ) {
                NotificationListenerRuntime.requestRebindIfGranted(this)
                render()
            }
        }
        if (Build.VERSION.SDK_INT >= 33) {
            addPermissionRow(panel, "Notificação do serviço", permissions.appNotifications, "Permitir") {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
            }
        }
        addPermissionRow(panel, "Execução sem restrição", permissions.unrestrictedBattery, "Configurar") {
            SystemSettingsNavigator.batteryOptimization(this)
        }
        if (isHyperOsFamily()) {
            addPermissionRow(panel, "Inicialização automática HyperOS", false, "Abrir") {
                SystemSettingsNavigator.hyperOsAutoStart(this)
            }
        }
        return panel
    }

    private fun addPermissionRow(
        parent: LinearLayout,
        label: String,
        granted: Boolean,
        buttonText: String,
        action: () -> Unit,
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(7), 0, dp(7))
        }
        row.addView(
            UiKit.title(this, label, 14f),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        row.addView(UiKit.chip(this, if (granted) "OK" else "Pendente", granted))
        val button = UiKit.actionButton(this, buttonText).apply { setOnClickListener { action() } }
        row.addView(
            button,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(44)).apply {
                marginStart = dp(8)
            },
        )
        parent.addView(row)
    }

    private fun featurePanel(settings: DynamicSettings): View {
        val panel = UiKit.panel(this)
        panel.addView(UiKit.title(this, "Recursos", 18f))
        panel.addView(UiKit.subtitle(this, "Escolha quais tipos de evento podem ocupar a ilha."))
        panel.addView(UiKit.spacer(this, 8))
        addSwitch(panel, "Notificações", settings.showNotifications, container.settingsRepository::setShowNotifications)
        addSwitch(panel, "Controles de mídia", settings.showMedia, container.settingsRepository::setShowMedia)
        addSwitch(panel, "Bateria e carregamento", settings.showBattery, container.settingsRepository::setShowBattery)
        addSwitch(panel, "Downloads e progresso", settings.showProgress, container.settingsRepository::setShowProgress)
        addSwitch(panel, "Alarmes e temporizadores", settings.showTimers, container.settingsRepository::setShowTimers)
        addSwitch(panel, "Ocultar conteúdo sensível", settings.privacyMode, container.settingsRepository::setPrivacyMode)
        addSwitch(panel, "Resposta tátil", settings.haptics, container.settingsRepository::setHaptics)
        return panel
    }

    private fun addSwitch(
        parent: LinearLayout,
        label: String,
        checked: Boolean,
        onChange: (Boolean) -> Unit,
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(5), 0, dp(5))
        }
        row.addView(
            UiKit.title(this, label, 14f),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        row.addView(
            Switch(this).apply {
                isChecked = checked
                setOnCheckedChangeListener { _, value -> onChange(value) }
            },
        )
        parent.addView(row)
    }

    private fun layoutPanel(settings: DynamicSettings): View {
        val panel = UiKit.panel(this)
        panel.addView(UiKit.title(this, "Ajuste fino", 18f))
        panel.addView(
            UiKit.subtitle(
                this,
                "O posicionamento usa o recorte real da tela; estes controles servem apenas para refinamento visual.",
            ),
        )
        panel.addView(UiKit.spacer(this, 8))
        addSlider(panel, "Largura recolhida", settings.collapsedWidthDp, 72, 240, "dp", container.settingsRepository::setCollapsedWidth)
        addSlider(panel, "Altura recolhida", settings.collapsedHeightDp, 28, 72, "dp", container.settingsRepository::setCollapsedHeight)
        addSlider(panel, "Largura expandida", settings.expandedWidthDp, 220, 480, "dp", container.settingsRepository::setExpandedWidth)
        addSlider(panel, "Altura expandida", settings.expandedHeightDp, 64, 180, "dp", container.settingsRepository::setExpandedHeight)
        addSlider(panel, "Posição vertical", settings.verticalOffsetDp, -80, 80, "dp", container.settingsRepository::setVerticalOffset)
        addSlider(panel, "Animação", settings.animationDurationMs, 100, 700, "ms", container.settingsRepository::setAnimationDuration)
        addSlider(panel, "Tempo da notificação", settings.notificationDurationSeconds, 2, 15, "s", container.settingsRepository::setNotificationDuration)
        return panel
    }

    private fun addSlider(
        parent: LinearLayout,
        label: String,
        value: Int,
        min: Int,
        max: Int,
        unit: String,
        onChange: (Int) -> Unit,
    ) {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            UiKit.title(this, label, 13f),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        val valueView = UiKit.subtitle(this, value.toString() + " " + unit, 12f)
        header.addView(valueView)
        parent.addView(header)
        parent.addView(
            SeekBar(this).apply {
                this.max = max - min
                progress = value.coerceIn(min, max) - min
                setOnSeekBarChangeListener(
                    object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(
                            seekBar: SeekBar?,
                            progress: Int,
                            fromUser: Boolean,
                        ) {
                            val actual = min + progress
                            valueView.text = actual.toString() + " " + unit
                            if (fromUser) onChange(actual)
                        }

                        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
                    },
                )
            },
        )
    }

    private fun diagnosticsPanel(): View {
        val panel = UiKit.panel(this)
        panel.addView(UiKit.title(this, "Diagnóstico local", 18f))
        panel.addView(
            UiKit.subtitle(
                this,
                "Registra apenas eventos técnicos do SX Dynamic em arquivo local limitado. Conteúdo de notificações não é gravado.",
            ),
        )
        panel.addView(UiKit.spacer(this, 8))
        val entries = Diagnostics.snapshot().takeLast(8).reversed()
        if (entries.isEmpty()) {
            panel.addView(UiKit.subtitle(this, "Nenhum evento registrado nesta sessão."))
        } else {
            entries.forEach { entry ->
                panel.addView(
                    UiKit.subtitle(
                        this,
                        entry.level + " · " + entry.source + " · " + entry.message,
                        11f,
                    ).apply {
                        setPadding(0, dp(3), 0, dp(3))
                    },
                )
            }
        }
        panel.addView(UiKit.spacer(this, 10))
        panel.addView(
            UiKit.actionButton(this, "Testar exibição da ilha").apply {
                setOnClickListener {
                    if (Settings.canDrawOverlays(this@MainActivity)) {
                        container.settingsRepository.setEnabled(true)
                        OverlayServiceController.start(this@MainActivity)
                        container.overlayCoordinator.showNotification(
                            IslandContent.NotificationEvent(
                                key = "__sx_test__",
                                packageName = packageName,
                                appName = "Teste",
                                title = "Notificação de teste",
                                text = "Se você está vendo isto, o overlay está funcionando.",
                                action = null,
                            ),
                        )
                    } else {
                        SystemSettingsNavigator.overlay(this@MainActivity)
                    }
                }
            },
        )
        panel.addView(UiKit.spacer(this, 8))
        panel.addView(
            UiKit.actionButton(this, "Limpar diagnóstico").apply {
                setOnClickListener {
                    Diagnostics.clear()
                    render()
                }
            },
        )
        return panel
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATIONS) render()
    }

    private fun isHyperOsFamily(): Boolean {
        val maker = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return maker.contains("xiaomi") || brand.contains("poco") || brand.contains("redmi")
    }

    private companion object {
        const val REQUEST_NOTIFICATIONS = 1201
    }
}
