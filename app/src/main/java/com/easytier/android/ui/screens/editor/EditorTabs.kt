package com.easytier.android.ui.screens.editor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.easytier.android.R
import com.easytier.android.core.toml.TomlGenerator
import com.easytier.android.data.model.NetworkConfig
import com.easytier.android.data.model.NetworkingMethod
import com.easytier.android.data.model.PortForwardEntry
import com.easytier.android.ui.components.AppCard
import com.easytier.android.ui.components.CardDivider
import com.easytier.android.ui.components.CollapsibleCardSection
import com.easytier.android.ui.components.EmptyState
import com.easytier.android.ui.components.IntField
import com.easytier.android.ui.components.LongField
import com.easytier.android.ui.components.PillBadge
import com.easytier.android.ui.components.SectionHeader
import com.easytier.android.ui.components.StringListEditor
import com.easytier.android.ui.components.SwitchRow
import com.easytier.android.ui.components.extractProtocolBadge
import com.easytier.android.ui.icons.AppIcons

private const val OFFICIAL_PUBLIC_SERVER = "tcp://public.easytier.cn:11010"
private val PEER_PROTOCOL_PREFIXES = listOf("tcp://", "udp://", "wg://", "ws://", "wss://")
private val LISTENER_PROTOCOL_PREFIXES = listOf("tcp://", "udp://", "wg://", "ws://", "wss://")

private val CardInnerModifier = Modifier
    .fillMaxWidth()
    .padding(horizontal = 16.dp)

// ---------- Basic 标签 ----------

@Composable
internal fun BasicTab(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) {
    SectionHeader(
        stringResource(R.string.editor_section_basic),
        modifier = Modifier.padding(horizontal = 4.dp),
    )
    AppCard {
        Column(Modifier.padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NetworkNameField(config, update)
            NetworkSecretField(config, update)
        }
    }

    SectionHeader(
        stringResource(R.string.editor_section_address),
        modifier = Modifier.padding(horizontal = 4.dp),
    )
    AppCard {
        Column(Modifier.padding(vertical = 4.dp)) {
            DhcpControl(config, update, showDividerWhenExpanded = true)
        }
    }

    SectionHeader(
        stringResource(R.string.editor_section_networking),
        modifier = Modifier.padding(horizontal = 4.dp),
    )
    AppCard {
        Column(Modifier.padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NetworkingMethodControl(config, update)
            AnimatedContent(
                targetState = config.networkingMethod,
                transitionSpec = {
                    fadeIn(tween(180, easing = FastOutSlowInEasing)) togetherWith
                        fadeOut(tween(120))
                },
                label = "networkingMethodInput",
            ) { method ->
                when (method) {
                    NetworkingMethod.PublicServer -> PublicServerField(config, update)
                    NetworkingMethod.Manual -> PeerUrlsField(config, update)
                    NetworkingMethod.Standalone -> Text(
                        stringResource(R.string.editor_standalone_desc),
                        modifier = CardInnerModifier,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    SectionHeader(
        stringResource(R.string.editor_section_runtime),
        modifier = Modifier.padding(horizontal = 4.dp),
    )
    AppCard {
        Column(Modifier.padding(vertical = 4.dp)) {
            ExitNodeSwitch(config, update)
        }
    }
}

// ---------- Advanced 标签 ----------

@Composable
internal fun AdvancedTab(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) {
    var expandP2p by rememberSaveable { mutableStateOf(false) }
    var expandTransport by rememberSaveable { mutableStateOf(false) }
    var expandSecurity by rememberSaveable { mutableStateOf(false) }

    SectionHeader(
        stringResource(R.string.editor_section_details),
        modifier = Modifier.padding(horizontal = 4.dp),
    )
    AppCard {
        Column(Modifier.padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HostnameField(config, update)
            DevNameField(config, update)
            MtuField(config, update)
            ListenerUrlsField(config, update)
        }
    }

    Spacer(Modifier.height(12.dp))
    AppCard {
        Column(Modifier.padding(vertical = 4.dp)) {
            MagicDnsControl(config, update)
        }
    }

    SectionHeader(
        stringResource(R.string.editor_section_common_flags),
        modifier = Modifier.padding(horizontal = 4.dp),
    )
    AppCard {
        Column(Modifier.padding(vertical = 4.dp)) {
            LatencyFirstSwitch(config, update)
            CardDivider()
            PrivateModeSwitch(config, update)
            CardDivider()
            DisableIpv6Switch(config, update)
            CardDivider()
            MultiThreadSwitch(config, update)
            CardDivider()
            NoTunSwitch(config, update)
        }
    }

    Spacer(Modifier.height(12.dp))
    CollapsibleCardSection(
        title = stringResource(R.string.editor_section_p2p),
        expanded = expandP2p,
        onToggle = { expandP2p = !expandP2p },
        modifiedCount = countModifiedP2pFlags(config),
    ) {
        DisableP2pSwitch(config, update)
        CardDivider()
        P2pOnlySwitch(config, update)
        CardDivider()
        LazyP2pSwitch(config, update)
        CardDivider()
        NeedP2pSwitch(config, update)
        CardDivider()
        DisableUdpHolePunchingSwitch(config, update)
        CardDivider()
        DisableTcpHolePunchingSwitch(config, update)
        CardDivider()
        DisableSymHolePunchingSwitch(config, update)
        CardDivider()
        DisableUpnpSwitch(config, update)
        CardDivider()
        DisableRelayDataSwitch(config, update)
        CardDivider()
        RelayAllPeerRpcSwitch(config, update)
    }

    Spacer(Modifier.height(12.dp))
    CollapsibleCardSection(
        title = stringResource(R.string.editor_section_transport),
        expanded = expandTransport,
        onToggle = { expandTransport = !expandTransport },
        modifiedCount = countModifiedTransportFlags(config),
    ) {
        KcpProxySwitch(config, update)
        CardDivider()
        DisableKcpInputSwitch(config, update)
        CardDivider()
        QuicProxySwitch(config, update)
        CardDivider()
        DisableQuicInputSwitch(config, update)
        CardDivider()
        SmoltcpSwitch(config, update)
        CardDivider()
        UdpBroadcastRelaySwitch(config, update)
    }

    Spacer(Modifier.height(12.dp))
    CollapsibleCardSection(
        title = stringResource(R.string.editor_section_security),
        expanded = expandSecurity,
        onToggle = { expandSecurity = !expandSecurity },
        modifiedCount = countModifiedSecurityFlags(config),
    ) {
        DisableEncryptionSwitch(config, update)
        CardDivider()
        BindDeviceSwitch(config, update)
        CardDivider()
        ProxyForwardBySystemSwitch(config, update)
    }

    SectionHeader(
        stringResource(R.string.editor_section_routes),
        modifier = Modifier.padding(horizontal = 4.dp),
    )
    AppCard {
        Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ProxyCidrsField(config, update)
            CardDivider()
            ManualRoutesControl(config, update)
            CardDivider()
            RelayWhitelistControl(config, update)
            CardDivider()
            ExitNodesField(config, update)
            CardDivider()
            RecvBpsLimitField(config, update)
        }
    }
}

// ---------- Port Forward 标签 ----------

@Composable
internal fun PortForwardTab(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) {
    SectionHeader(
        stringResource(R.string.editor_section_pf),
        modifier = Modifier.padding(horizontal = 4.dp),
    )
    Text(
        stringResource(R.string.editor_pf_desc),
        modifier = Modifier.padding(horizontal = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))

    PortForwardsControl(config, update)
}

// ---------- 跨 Tab 即时搜索结果 ----------

@Composable
internal fun EditorSearchResults(
    query: String,
    config: NetworkConfig,
    update: ((NetworkConfig) -> NetworkConfig) -> Unit,
) {
    val context = LocalContext.current
    val matches = remember(query, config) {
        filterEditorSettings(query, config) { meta ->
            val title = context.getString(meta.titleRes)
            val subtitle = meta.subtitleRes?.let { context.getString(it) }.orEmpty()
            "$title $subtitle"
        }
    }

    if (matches.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            EmptyState(
                icon = Icons.Filled.Search,
                title = stringResource(R.string.editor_search_empty_title),
                hint = stringResource(R.string.editor_search_empty_hint),
            )
        }
        return
    }

    val grouped = remember(matches) { matches.groupBy { it.category } }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        grouped.forEach { (category, metas) ->
            val tabLabel = stringResource(
                when (category.tabIndex) {
                    0 -> R.string.editor_tab_basic
                    1 -> R.string.editor_tab_advanced
                    else -> R.string.editor_tab_port_forward
                },
            )
            SectionHeader(
                title = stringResource(category.titleRes),
                modifier = Modifier.padding(horizontal = 4.dp),
                trailing = {
                    PillBadge(
                        text = tabLabel,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            if (category == EditorCategory.PortForward) {
                PortForwardsControl(config, update)
            } else {
                AppCard {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        metas.forEachIndexed { idx, meta ->
                            if (idx > 0) {
                                Spacer(Modifier.height(6.dp))
                                CardDivider()
                                Spacer(Modifier.height(6.dp))
                            }
                            RenderSearchSettingItem(meta.id, config, update)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun RenderSearchSettingItem(
    id: EditorSettingId,
    config: NetworkConfig,
    update: ((NetworkConfig) -> NetworkConfig) -> Unit,
) {
    when (id) {
        EditorSettingId.NetworkName -> NetworkNameField(config, update)
        EditorSettingId.NetworkSecret -> NetworkSecretField(config, update)
        EditorSettingId.Dhcp -> DhcpControl(config, update, showDividerWhenExpanded = false)
        EditorSettingId.VirtualIpv4 -> VirtualIpv4Fields(config, update)
        EditorSettingId.NetworkingMethod -> NetworkingMethodControl(config, update)
        EditorSettingId.PublicServerUrl -> PublicServerField(config, update)
        EditorSettingId.PeerUrls -> PeerUrlsField(config, update)
        EditorSettingId.EnableExitNode -> ExitNodeSwitch(config, update)
        EditorSettingId.Hostname -> HostnameField(config, update)
        EditorSettingId.DevName -> DevNameField(config, update)
        EditorSettingId.Mtu -> MtuField(config, update)
        EditorSettingId.ListenerUrls -> ListenerUrlsField(config, update)
        EditorSettingId.MagicDns -> MagicDnsControl(config, update)
        EditorSettingId.DnsZone -> DnsZoneField(config, update)
        EditorSettingId.LatencyFirst -> LatencyFirstSwitch(config, update)
        EditorSettingId.EnablePrivateMode -> PrivateModeSwitch(config, update)
        EditorSettingId.DisableIpv6 -> DisableIpv6Switch(config, update)
        EditorSettingId.MultiThread -> MultiThreadSwitch(config, update)
        EditorSettingId.NoTun -> NoTunSwitch(config, update)
        EditorSettingId.DisableP2p -> DisableP2pSwitch(config, update)
        EditorSettingId.P2pOnly -> P2pOnlySwitch(config, update)
        EditorSettingId.LazyP2p -> LazyP2pSwitch(config, update)
        EditorSettingId.NeedP2p -> NeedP2pSwitch(config, update)
        EditorSettingId.DisableUdpHolePunching -> DisableUdpHolePunchingSwitch(config, update)
        EditorSettingId.DisableTcpHolePunching -> DisableTcpHolePunchingSwitch(config, update)
        EditorSettingId.DisableSymHolePunching -> DisableSymHolePunchingSwitch(config, update)
        EditorSettingId.DisableUpnp -> DisableUpnpSwitch(config, update)
        EditorSettingId.DisableRelayData -> DisableRelayDataSwitch(config, update)
        EditorSettingId.RelayAllPeerRpc -> RelayAllPeerRpcSwitch(config, update)
        EditorSettingId.EnableKcpProxy -> KcpProxySwitch(config, update)
        EditorSettingId.DisableKcpInput -> DisableKcpInputSwitch(config, update)
        EditorSettingId.EnableQuicProxy -> QuicProxySwitch(config, update)
        EditorSettingId.DisableQuicInput -> DisableQuicInputSwitch(config, update)
        EditorSettingId.UseSmoltcp -> SmoltcpSwitch(config, update)
        EditorSettingId.EnableUdpBroadcastRelay -> UdpBroadcastRelaySwitch(config, update)
        EditorSettingId.DisableEncryption -> DisableEncryptionSwitch(config, update)
        EditorSettingId.BindDevice -> BindDeviceSwitch(config, update)
        EditorSettingId.ProxyForwardBySystem -> ProxyForwardBySystemSwitch(config, update)
        EditorSettingId.ProxyCidrs -> ProxyCidrsField(config, update)
        EditorSettingId.ManualRoutes -> ManualRoutesControl(config, update)
        EditorSettingId.RelayWhitelist -> RelayWhitelistControl(config, update)
        EditorSettingId.ExitNodes -> ExitNodesField(config, update)
        EditorSettingId.RecvBpsLimit -> RecvBpsLimitField(config, update)
        EditorSettingId.PortForwards -> PortForwardsControl(config, update)
    }
}

// ---------- 可复用原子控件 ----------

@Composable
private fun NetworkNameField(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) {
    OutlinedTextField(
        value = config.networkName,
        onValueChange = { v -> update { it.copy(networkName = v) } },
        label = { Text(stringResource(R.string.editor_name)) },
        singleLine = true,
        modifier = CardInnerModifier,
    )
}

@Composable
private fun NetworkSecretField(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) {
    var showSecret by remember { mutableStateOf(false) }
    val hideLabel = stringResource(R.string.editor_hide)
    val showLabel = stringResource(R.string.editor_show)
    OutlinedTextField(
        value = config.networkSecret ?: "",
        onValueChange = { v -> update { it.copy(networkSecret = v.ifBlank { null }) } },
        label = { Text(stringResource(R.string.editor_secret)) },
        placeholder = { Text(stringResource(R.string.editor_secret_hint)) },
        singleLine = true,
        visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { showSecret = !showSecret }) {
                Icon(
                    imageVector = if (showSecret) AppIcons.Visibility else AppIcons.VisibilityOff,
                    contentDescription = if (showSecret) hideLabel else showLabel,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        modifier = CardInnerModifier,
    )
}

@Composable
private fun DhcpControl(
    config: NetworkConfig,
    update: ((NetworkConfig) -> NetworkConfig) -> Unit,
    showDividerWhenExpanded: Boolean,
) {
    SwitchRow(
        title = stringResource(R.string.editor_dhcp),
        subtitle = stringResource(R.string.editor_dhcp_subtitle),
        checked = config.dhcp,
        onCheckedChange = { v -> update { it.copy(dhcp = v) } },
    )
    AnimatedVisibility(
        visible = config.dhcp && config.networkingMethod == NetworkingMethod.Standalone,
        enter = expandVertically(tween(200, easing = FastOutSlowInEasing)) + fadeIn(tween(160)),
        exit = shrinkVertically(tween(160, easing = FastOutSlowInEasing)) + fadeOut(tween(120)),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                stringResource(R.string.editor_standalone_dhcp_warn),
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
    AnimatedVisibility(
        visible = !config.dhcp,
        enter = expandVertically(tween(220, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
        exit = shrinkVertically(tween(180, easing = FastOutSlowInEasing)) + fadeOut(tween(120)),
    ) {
        Column {
            if (showDividerWhenExpanded) {
                CardDivider()
                Spacer(Modifier.height(12.dp))
            }
            VirtualIpv4Fields(config, update)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun VirtualIpv4Fields(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) {
    val prefixLenLabel = stringResource(R.string.editor_prefix_len)
    Row(
        CardInnerModifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = config.virtualIpv4 ?: "",
            onValueChange = { v -> update { it.copy(virtualIpv4 = v) } },
            label = { Text(stringResource(R.string.editor_virtual_ipv4)) },
            placeholder = { Text("10.126.126.1") },
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            singleLine = true,
            modifier = Modifier.weight(2f),
        )
        IntField(
            value = config.networkLength,
            onValueChange = { v -> update { it.copy(networkLength = v ?: 24) } },
            label = prefixLenLabel,
            range = 0..32,
            allowEmpty = false,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun NetworkingMethodControl(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) {
    SingleChoiceSegmentedButtonRow(CardInnerModifier) {
        NetworkingMethod.entries.forEachIndexed { i, method ->
            SegmentedButton(
                selected = config.networkingMethod == method,
                onClick = { update { it.copy(networkingMethod = method) } },
                shape = SegmentedButtonDefaults.itemShape(i, NetworkingMethod.entries.size),
            ) {
                Text(stringResource(method.labelRes), maxLines = 1)
            }
        }
    }
}

@Composable
private fun PublicServerField(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) {
    Column(CardInnerModifier) {
        OutlinedTextField(
            value = config.publicServerUrl ?: "",
            onValueChange = { v -> update { it.copy(publicServerUrl = v) } },
            label = { Text(stringResource(R.string.editor_public_server_url)) },
            placeholder = { Text(OFFICIAL_PUBLIC_SERVER) },
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (config.publicServerUrl?.trim() != OFFICIAL_PUBLIC_SERVER) {
            AssistChip(
                onClick = { update { it.copy(publicServerUrl = OFFICIAL_PUBLIC_SERVER) } },
                label = { Text(stringResource(R.string.editor_use_official_server)) },
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun PeerUrlsField(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) {
    StringListEditor(
        label = stringResource(R.string.editor_initial_peers),
        items = config.peerUrls,
        onChange = { list -> update { it.copy(peerUrls = list) } },
        placeholder = "tcp://203.0.113.10:11010",
        emptyHint = stringResource(R.string.editor_peers_empty),
        addButtonText = stringResource(R.string.editor_peers_add),
        badgeProvider = ::extractProtocolBadge,
        quickPrefixes = PEER_PROTOCOL_PREFIXES,
        modifier = CardInnerModifier,
    )
}

@Composable
private fun ExitNodeSwitch(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) {
    SwitchRow(
        title = stringResource(R.string.editor_exit_node),
        subtitle = stringResource(R.string.editor_exit_node_subtitle),
        checked = config.enableExitNode ?: false,
        onCheckedChange = { v -> update { it.copy(enableExitNode = v) } },
    )
}

@Composable
private fun HostnameField(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) {
    OutlinedTextField(
        value = config.hostname ?: "",
        onValueChange = { v -> update { it.copy(hostname = v.ifBlank { null }) } },
        label = { Text(stringResource(R.string.editor_hostname)) },
        singleLine = true,
        modifier = CardInnerModifier,
    )
}

@Composable
private fun DevNameField(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) {
    OutlinedTextField(
        value = config.devName ?: "",
        onValueChange = { v -> update { it.copy(devName = v.ifBlank { null }) } },
        label = { Text(stringResource(R.string.editor_dev_name)) },
        singleLine = true,
        modifier = CardInnerModifier,
    )
}

@Composable
private fun MtuField(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) {
    IntField(
        value = config.mtu,
        onValueChange = { v -> update { it.copy(mtu = v) } },
        label = stringResource(R.string.editor_mtu),
        range = 400..1380,
        supporting = stringResource(R.string.editor_mtu_support),
        allowEmpty = true,
        modifier = CardInnerModifier,
    )
}

@Composable
private fun ListenerUrlsField(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) {
    StringListEditor(
        label = stringResource(R.string.editor_listeners),
        items = config.listenerUrls,
        onChange = { list -> update { it.copy(listenerUrls = list) } },
        placeholder = "tcp://0.0.0.0:11010",
        emptyHint = stringResource(R.string.editor_listeners_empty),
        addButtonText = stringResource(R.string.editor_listeners_add),
        badgeProvider = ::extractProtocolBadge,
        quickPrefixes = LISTENER_PROTOCOL_PREFIXES,
        modifier = CardInnerModifier,
    )
}

@Composable
private fun MagicDnsControl(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) {
    SwitchRow(
        title = stringResource(R.string.flag_magic_dns),
        subtitle = stringResource(R.string.flag_magic_dns_subtitle),
        checked = config.enableMagicDns,
        onCheckedChange = { v -> update { it.copy(enableMagicDns = v) } },
    )
    AnimatedVisibility(
        visible = config.enableMagicDns,
        enter = expandVertically(tween(220, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
        exit = shrinkVertically(tween(180, easing = FastOutSlowInEasing)) + fadeOut(tween(120)),
    ) {
        Column {
            CardDivider()
            Spacer(Modifier.height(12.dp))
            DnsZoneField(config, update)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun DnsZoneField(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) {
    OutlinedTextField(
        value = config.tldDnsZone ?: "",
        onValueChange = { v -> update { it.copy(tldDnsZone = v.trim().ifBlank { null }) } },
        label = { Text(stringResource(R.string.editor_dns_zone)) },
        placeholder = { Text(config.networkName) },
        supportingText = {
            Text(stringResource(R.string.editor_dns_zone_support, TomlGenerator.effectiveDnsZone(config)))
        },
        singleLine = true,
        modifier = CardInnerModifier,
    )
}

@Composable
private fun LatencyFirstSwitch(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) =
    SwitchRow(
        title = stringResource(R.string.flag_latency_first),
        subtitle = stringResource(R.string.flag_latency_first_subtitle),
        checked = config.latencyFirst ?: false,
    ) { v -> update { it.copy(latencyFirst = v) } }

@Composable
private fun PrivateModeSwitch(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) =
    SwitchRow(
        title = stringResource(R.string.flag_private_mode),
        subtitle = stringResource(R.string.flag_private_mode_subtitle),
        checked = config.enablePrivateMode ?: false,
    ) { v -> update { it.copy(enablePrivateMode = v) } }

@Composable
private fun DisableIpv6Switch(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) =
    SwitchRow(
        title = stringResource(R.string.flag_disable_ipv6),
        subtitle = stringResource(R.string.flag_disable_ipv6_subtitle),
        checked = config.disableIpv6 ?: false,
    ) { v -> update { it.copy(disableIpv6 = v) } }

@Composable
private fun MultiThreadSwitch(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) =
    SwitchRow(
        title = stringResource(R.string.flag_multi_thread),
        subtitle = stringResource(R.string.flag_multi_thread_subtitle),
        checked = config.multiThread ?: true,
    ) { v -> update { it.copy(multiThread = v) } }

@Composable
private fun NoTunSwitch(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) =
    SwitchRow(
        title = stringResource(R.string.flag_no_tun),
        subtitle = stringResource(R.string.flag_no_tun_subtitle),
        checked = config.noTun ?: false,
    ) { v -> update { it.copy(noTun = v) } }

@Composable
private fun DisableP2pSwitch(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) =
    SwitchRow(
        title = stringResource(R.string.flag_disable_p2p),
        subtitle = stringResource(R.string.flag_disable_p2p_subtitle),
        checked = config.disableP2p ?: false,
    ) { v -> update { it.copy(disableP2p = v) } }

@Composable
private fun P2pOnlySwitch(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) =
    SwitchRow(
        title = stringResource(R.string.flag_p2p_only),
        subtitle = stringResource(R.string.flag_p2p_only_subtitle),
        checked = config.p2pOnly ?: false,
    ) { v -> update { it.copy(p2pOnly = v) } }

@Composable
private fun LazyP2pSwitch(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) =
    SwitchRow(
        title = stringResource(R.string.flag_lazy_p2p),
        subtitle = stringResource(R.string.flag_lazy_p2p_subtitle),
        checked = config.lazyP2p ?: false,
    ) { v -> update { it.copy(lazyP2p = v) } }

@Composable
private fun NeedP2pSwitch(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) =
    SwitchRow(
        title = stringResource(R.string.flag_need_p2p),
        subtitle = stringResource(R.string.flag_need_p2p_subtitle),
        checked = config.needP2p ?: false,
    ) { v -> update { it.copy(needP2p = v) } }

@Composable
private fun DisableUdpHolePunchingSwitch(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) =
    SwitchRow(
        title = stringResource(R.string.flag_disable_udp_hole_punching),
        subtitle = stringResource(R.string.flag_disable_udp_hole_punching_subtitle),
        checked = config.disableUdpHolePunching ?: false,
    ) { v -> update { it.copy(disableUdpHolePunching = v) } }

@Composable
private fun DisableTcpHolePunchingSwitch(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) =
    SwitchRow(
        title = stringResource(R.string.flag_disable_tcp_hole_punching),
        subtitle = stringResource(R.string.flag_disable_tcp_hole_punching_subtitle),
        checked = config.disableTcpHolePunching ?: false,
    ) { v -> update { it.copy(disableTcpHolePunching = v) } }

@Composable
private fun DisableSymHolePunchingSwitch(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) =
    SwitchRow(
        title = stringResource(R.string.flag_disable_sym_hole_punching),
        subtitle = stringResource(R.string.flag_disable_sym_hole_punching_subtitle),
        checked = config.disableSymHolePunching ?: false,
    ) { v -> update { it.copy(disableSymHolePunching = v) } }

@Composable
private fun DisableUpnpSwitch(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) =
    SwitchRow(
        title = stringResource(R.string.flag_disable_upnp),
        subtitle = stringResource(R.string.flag_disable_upnp_subtitle),
        checked = config.disableUpnp ?: false,
    ) { v -> update { it.copy(disableUpnp = v) } }

@Composable
private fun DisableRelayDataSwitch(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) =
    SwitchRow(
        title = stringResource(R.string.flag_disable_relay_data),
        subtitle = stringResource(R.string.flag_disable_relay_data_subtitle),
        checked = config.disableRelayData ?: false,
    ) { v -> update { it.copy(disableRelayData = v) } }

@Composable
private fun RelayAllPeerRpcSwitch(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) =
    SwitchRow(
        title = stringResource(R.string.flag_relay_all_peer_rpc),
        subtitle = stringResource(R.string.flag_relay_all_peer_rpc_subtitle),
        checked = config.relayAllPeerRpc ?: false,
    ) { v -> update { it.copy(relayAllPeerRpc = v) } }

@Composable
private fun KcpProxySwitch(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) =
    SwitchRow(
        title = stringResource(R.string.flag_kcp_proxy),
        subtitle = stringResource(R.string.flag_kcp_proxy_subtitle),
        checked = config.enableKcpProxy ?: false,
    ) { v -> update { it.copy(enableKcpProxy = v) } }

@Composable
private fun DisableKcpInputSwitch(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) =
    SwitchRow(
        title = stringResource(R.string.flag_disable_kcp_input),
        subtitle = stringResource(R.string.flag_disable_kcp_input_subtitle),
        checked = config.disableKcpInput ?: false,
    ) { v -> update { it.copy(disableKcpInput = v) } }

@Composable
private fun QuicProxySwitch(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) =
    SwitchRow(
        title = stringResource(R.string.flag_quic_proxy),
        subtitle = stringResource(R.string.flag_quic_proxy_subtitle),
        checked = config.enableQuicProxy ?: false,
    ) { v -> update { it.copy(enableQuicProxy = v) } }

@Composable
private fun DisableQuicInputSwitch(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) =
    SwitchRow(
        title = stringResource(R.string.flag_disable_quic_input),
        subtitle = stringResource(R.string.flag_disable_quic_input_subtitle),
        checked = config.disableQuicInput ?: false,
    ) { v -> update { it.copy(disableQuicInput = v) } }

@Composable
private fun SmoltcpSwitch(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) =
    SwitchRow(
        title = stringResource(R.string.flag_smoltcp),
        subtitle = stringResource(R.string.flag_smoltcp_subtitle),
        checked = config.useSmoltcp ?: false,
    ) { v -> update { it.copy(useSmoltcp = v) } }

@Composable
private fun UdpBroadcastRelaySwitch(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) =
    SwitchRow(
        title = stringResource(R.string.flag_udp_broadcast_relay),
        subtitle = stringResource(R.string.flag_udp_broadcast_relay_subtitle),
        checked = config.enableUdpBroadcastRelay ?: false,
    ) { v -> update { it.copy(enableUdpBroadcastRelay = v) } }

@Composable
private fun DisableEncryptionSwitch(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) =
    SwitchRow(
        title = stringResource(R.string.flag_disable_encryption),
        subtitle = stringResource(R.string.flag_disable_encryption_subtitle),
        checked = config.disableEncryption ?: false,
    ) { v -> update { it.copy(disableEncryption = v) } }

@Composable
private fun BindDeviceSwitch(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) =
    SwitchRow(
        title = stringResource(R.string.flag_bind_device),
        subtitle = stringResource(R.string.flag_bind_device_subtitle),
        checked = config.bindDevice ?: true,
    ) { v -> update { it.copy(bindDevice = v) } }

@Composable
private fun ProxyForwardBySystemSwitch(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) =
    SwitchRow(
        title = stringResource(R.string.flag_proxy_forward_by_system),
        subtitle = stringResource(R.string.flag_proxy_forward_by_system_subtitle),
        checked = config.proxyForwardBySystem ?: false,
    ) { v -> update { it.copy(proxyForwardBySystem = v) } }

@Composable
private fun ProxyCidrsField(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) {
    StringListEditor(
        label = stringResource(R.string.editor_proxy_cidrs),
        items = config.proxyCidrs,
        onChange = { list -> update { it.copy(proxyCidrs = list) } },
        placeholder = "10.0.0.0/24",
        emptyHint = stringResource(R.string.editor_proxy_cidrs_empty),
        addButtonText = stringResource(R.string.editor_proxy_cidrs_add),
        modifier = CardInnerModifier,
    )
}

@Composable
private fun ManualRoutesControl(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) {
    Column {
        SwitchRow(
            title = stringResource(R.string.flag_manual_routes),
            subtitle = stringResource(R.string.flag_manual_routes_subtitle),
            checked = config.enableManualRoutes ?: false,
        ) { v -> update { it.copy(enableManualRoutes = v) } }
        AnimatedVisibility(
            visible = config.enableManualRoutes == true,
            enter = expandVertically(tween(220, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
            exit = shrinkVertically(tween(180, easing = FastOutSlowInEasing)) + fadeOut(tween(120)),
        ) {
            StringListEditor(
                label = stringResource(R.string.editor_route_table),
                items = config.routes ?: emptyList(),
                onChange = { list -> update { it.copy(routes = list) } },
                placeholder = "10.147.0.0/16",
                emptyHint = stringResource(R.string.editor_routes_empty),
                addButtonText = stringResource(R.string.editor_routes_add),
                modifier = CardInnerModifier.padding(top = 6.dp, bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun RelayWhitelistControl(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) {
    Column {
        SwitchRow(
            title = stringResource(R.string.flag_relay_whitelist),
            subtitle = stringResource(R.string.flag_relay_whitelist_subtitle),
            checked = config.enableRelayNetworkWhitelist ?: false,
        ) { v -> update { it.copy(enableRelayNetworkWhitelist = v) } }
        AnimatedVisibility(
            visible = config.enableRelayNetworkWhitelist == true,
            enter = expandVertically(tween(220, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
            exit = shrinkVertically(tween(180, easing = FastOutSlowInEasing)) + fadeOut(tween(120)),
        ) {
            StringListEditor(
                label = stringResource(R.string.editor_whitelist_cidrs),
                items = config.relayNetworkWhitelist ?: emptyList(),
                onChange = { list -> update { it.copy(relayNetworkWhitelist = list) } },
                placeholder = "172.16.0.0/12",
                emptyHint = stringResource(R.string.editor_whitelist_empty),
                addButtonText = stringResource(R.string.editor_whitelist_add),
                modifier = CardInnerModifier.padding(top = 6.dp, bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun ExitNodesField(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) {
    StringListEditor(
        label = stringResource(R.string.editor_exit_nodes),
        items = config.exitNodes ?: emptyList(),
        onChange = { list -> update { it.copy(exitNodes = list) } },
        placeholder = "10.126.126.1",
        emptyHint = stringResource(R.string.editor_exit_nodes_empty),
        addButtonText = stringResource(R.string.editor_exit_nodes_add),
        modifier = CardInnerModifier,
    )
}

@Composable
private fun RecvBpsLimitField(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) {
    val supporting = config.instanceRecvBpsLimit?.takeIf { it > 0L }?.let { bps ->
        stringResource(R.string.editor_bps_support_preview, formatBitratePreview(bps))
    } ?: stringResource(R.string.editor_bps_support_empty)
    LongField(
        value = config.instanceRecvBpsLimit,
        onValueChange = { v -> update { it.copy(instanceRecvBpsLimit = v) } },
        label = stringResource(R.string.editor_recv_bps_limit),
        supporting = supporting,
        allowEmpty = true,
        modifier = CardInnerModifier,
    )
}

@Composable
private fun PortForwardsControl(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) {
    Column(Modifier.animateContentSize(animationSpec = tween(durationMillis = 200))) {
        if (config.portForwards.isEmpty()) {
            AppCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.editor_pf_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    FilledTonalButton(
                        onClick = {
                            update { it.copy(portForwards = it.portForwards + PortForwardEntry()) }
                        },
                    ) {
                        Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                        Text(stringResource(R.string.editor_pf_add), Modifier.padding(start = 6.dp))
                    }
                }
            }
            return@Column
        }

        config.portForwards.forEachIndexed { index, pf ->
            PortForwardEditor(
                pf = pf,
                onChange = { newPf ->
                    update { c ->
                        c.copy(portForwards = c.portForwards.toMutableList().apply { set(index, newPf) })
                    }
                },
                onDelete = {
                    update { c ->
                        c.copy(portForwards = c.portForwards.toMutableList().apply { removeAt(index) })
                    }
                },
            )
            Spacer(Modifier.height(12.dp))
        }

        FilledTonalButton(
            onClick = {
                update { it.copy(portForwards = it.portForwards + PortForwardEntry()) }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
            Text(stringResource(R.string.editor_pf_add), Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun PortForwardEditor(
    pf: PortForwardEntry,
    onChange: (PortForwardEntry) -> Unit,
    onDelete: () -> Unit,
) {
    val deleteLabel = stringResource(R.string.common_delete)
    val bindPortLabel = stringResource(R.string.editor_pf_bind_port)
    val dstPortLabel = stringResource(R.string.editor_pf_dst_port)
    val dstPreview = "${pf.dstIp.ifBlank { "?" }}:${pf.dstPort}"
    AppCard {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PillBadge(
                    text = pf.proto.uppercase(),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    "${pf.bindIp}:${pf.bindPort} → $dstPreview",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Delete, deleteLabel, tint = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = pf.bindIp,
                    onValueChange = { v -> onChange(pf.copy(bindIp = v)) },
                    label = { Text(stringResource(R.string.editor_pf_bind_ip)) },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    singleLine = true,
                    modifier = Modifier.weight(2f),
                )
                IntField(
                    value = pf.bindPort,
                    onValueChange = { v -> onChange(pf.copy(bindPort = v ?: 0)) },
                    label = bindPortLabel,
                    range = 1..65535,
                    allowEmpty = false,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = pf.dstIp,
                    onValueChange = { v -> onChange(pf.copy(dstIp = v)) },
                    label = { Text(stringResource(R.string.editor_pf_dst_ip)) },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    singleLine = true,
                    modifier = Modifier.weight(2f),
                )
                IntField(
                    value = pf.dstPort,
                    onValueChange = { v -> onChange(pf.copy(dstPort = v ?: 0)) },
                    label = dstPortLabel,
                    range = 1..65535,
                    allowEmpty = false,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("tcp", "udp").forEachIndexed { i, proto ->
                    SegmentedButton(
                        selected = pf.proto == proto,
                        onClick = { onChange(pf.copy(proto = proto)) },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                    ) { Text(proto.uppercase()) }
                }
            }
        }
    }
}
