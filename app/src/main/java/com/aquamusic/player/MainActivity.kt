package com.aquamusic.player

import android.content.ComponentName
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

private data class AudioTrack(val uri: Uri, val name: String, val artwork: ByteArray?)
private enum class AquaTheme(val label: String, val top: Color, val bottom: Color, val accent: Color) {
    AQUA("Aqua Glass", Color(0xFF071C2B), Color(0xFF123D55), Color(0xFF83E9FF)),
    CRYSTAL("Crystal", Color(0xFF182536), Color(0xFF35465D), Color(0xFFD6F5FF)),
    MIDNIGHT("Midnight", Color(0xFF090B19), Color(0xFF252344), Color(0xFFB7A9FF)),
    FROST("Frosted White", Color(0xFF243746), Color(0xFF607B8B), Color(0xFFEAFBFF)),
    EMERALD("Emerald Glass", Color(0xFF061D1A), Color(0xFF145044), Color(0xFF91F5D0))
}

class MainActivity : ComponentActivity() {
    private var controller by mutableStateOf<MediaController?>(null)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, MusicService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({ runCatching { controller = future.get() } }, MoreExecutors.directExecutor())
    }
    override fun onStop() {
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onStop()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AquaScreen(controller) }
    }
}

@Composable
private fun AquaScreen(player: MediaController?) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var tracks by remember { mutableStateOf(listOf<AudioTrack>()) }
    var selected by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf("home") }
    var theme by remember { mutableStateOf(AquaTheme.AQUA) }
    var transparency by remember { mutableFloatStateOf(78f) }
    var playPosition by remember { mutableFloatStateOf(0f) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var draggingSeek by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val pulse by animateFloatAsState(if (pressed) .93f else 1f, label = "glassTap")

    fun displayName(uri: Uri): String {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "Audio track"
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) name = c.getString(0) ?: name
            }
        }
        return name.substringAfterLast(':').ifBlank { "Audio track" }
    }
    fun readArtwork(uri: Uri): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try { retriever.setDataSource(context, uri); retriever.embeddedPicture }
        catch (_: Exception) { null }
        finally { runCatching { retriever.release() } }
    }
    fun mediaItem(track: AudioTrack): MediaItem {
        val metadata = MediaMetadata.Builder().setTitle(track.name)
            .apply { track.artwork?.let { setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER) } }.build()
        return MediaItem.Builder().setUri(track.uri).setMediaMetadata(metadata).build()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { picked ->
        if (picked.isNotEmpty()) {
            val added = picked.map { uri ->
                runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                AudioTrack(uri, displayName(uri), readArtwork(uri))
            }
            tracks = (tracks + added).distinctBy { it.uri }
            player?.let { p ->
                val all = (tracks + added).distinctBy { it.uri }
                p.setMediaItems(all.map { mediaItem(it) })
                p.prepare()
                if (all.isNotEmpty()) p.seekTo(all.size - added.size, 0)
            }
            selected = (tracks.size).coerceAtLeast(0)
            page = "player"
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                selected = player?.currentMediaItemIndex?.coerceAtLeast(0) ?: 0
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }

    val current = tracks.getOrNull(selected)
    LaunchedEffect(player, playing, selected) {
        while (true) {
            if (!draggingSeek) playPosition = (player?.currentPosition ?: 0L).coerceAtLeast(0L).toFloat()
            durationMs = (player?.duration ?: 0L).takeIf { it > 0L && it != androidx.media3.common.C.TIME_UNSET } ?: 0L
            delay(400)
        }
    }
    val background = Brush.linearGradient(listOf(theme.top, theme.bottom, theme.top))
    val glass = Color.White.copy(alpha = transparency / 100f * .17f)
    val edge = Color.White.copy(alpha = .24f)

    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(Modifier.fillMaxSize().background(background)) {
            Box(Modifier.size(300.dp).offset(x = 170.dp, y = 55.dp).background(Brush.radialGradient(listOf(theme.accent.copy(alpha = .22f), theme.accent.copy(alpha = .04f), Color.Transparent)), CircleShape))
            Box(Modifier.size(270.dp).offset(x = (-125).dp, y = 380.dp).background(Brush.radialGradient(listOf(Color(0xFF64C9FF).copy(alpha = .18f), Color.Transparent)), CircleShape))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.White.copy(alpha = .035f), Color.Transparent, Color.Black.copy(alpha = .12f)))))
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("AQUA MUSIC", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                        Text("YOUR MUSIC • YOUR MOOD", color = theme.accent, fontSize = 9.sp, letterSpacing = 2.sp)
                    }
                    GlassButton(Icons.Default.Settings, "Settings", theme.accent, glass, edge) { page = "settings" }
                }
                when (page) {
                    "settings" -> {
                        GlassPanel(glass, edge) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                GlassButton(Icons.Default.ArrowBack, "Back", theme.accent, glass, edge) { page = "home" }
                                Spacer(Modifier.width(12.dp))
                                Text("Settings", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(22.dp))
                            Text("APPEARANCE", color = theme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                            Spacer(Modifier.height(12.dp))
                            AquaTheme.values().forEach { item ->
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { theme = item }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(34.dp).background(Brush.linearGradient(listOf(item.top, item.bottom)), CircleShape).border(1.dp, Color.White.copy(alpha = .5f), CircleShape))
                                    Spacer(Modifier.width(12.dp))
                                    Text(item.label, color = Color.White, modifier = Modifier.weight(1f))
                                    if (theme == item) Icon(Icons.Default.CheckCircle, null, tint = item.accent)
                                }
                            }
                            Spacer(Modifier.height(18.dp))
                            Text("GLASS TRANSPARENCY", color = theme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                            Slider(value = transparency, onValueChange = { transparency = it }, valueRange = 25f..100f, colors = SliderDefaults.colors(thumbColor = theme.accent, activeTrackColor = theme.accent))
                            Text("${transparency.toInt()}% transparency", color = Color.White.copy(alpha = .75f), fontSize = 12.sp)
                        }
                    }
                    "player" -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GlassButton(Icons.Default.ArrowBack, "Back", theme.accent, glass, edge) { page = "home" }
                            Spacer(Modifier.width(12.dp))
                            Text("NOW PLAYING", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        }
                        Spacer(Modifier.height(22.dp))
                        AlbumCover(current?.artwork, 226, theme.accent, glass, edge, Modifier.align(Alignment.CenterHorizontally))
                        Spacer(Modifier.height(20.dp))
                        Text(current?.name ?: "Choose a song", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                        Text(if (current == null) "Your music library" else "LOCAL MUSIC", color = theme.accent.copy(alpha = .85f), fontSize = 10.sp, letterSpacing = 1.5.sp)
                        Spacer(Modifier.height(14.dp))
                        Slider(
                            value = if (draggingSeek) playPosition else playPosition.coerceAtMost(durationMs.toFloat().coerceAtLeast(1f)),
                            onValueChange = { draggingSeek = true; playPosition = it },
                            onValueChangeFinished = { player?.seekTo(playPosition.toLong()); draggingSeek = false },
                            valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                            enabled = durationMs > 0L,
                            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = theme.accent, inactiveTrackColor = Color.White.copy(alpha = .20f))
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatTime(playPosition.toLong()), color = Color.White.copy(alpha = .65f), fontSize = 10.sp)
                            Text(formatTime(durationMs), color = Color.White.copy(alpha = .65f), fontSize = 10.sp)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            GlassButton(Icons.Default.Shuffle, "Shuffle", theme.accent, glass, edge) { player?.shuffleModeEnabled = !(player?.shuffleModeEnabled ?: false) }
                            GlassButton(Icons.Default.SkipPrevious, "Previous", theme.accent, glass, edge) { player?.seekToPreviousMediaItem() }
                            Box(Modifier.size(72.dp).scale(pulse).background(Brush.linearGradient(listOf(Color.White, theme.accent)), CircleShape).border(1.dp, Color.White.copy(alpha = .8f), CircleShape).clickable {
                                pressed = true
                                if (player?.isPlaying == true) player.pause() else player?.let { if (it.mediaItemCount == 0 && tracks.isNotEmpty()) { it.setMediaItems(tracks.map { t -> mediaItem(t) }); it.prepare() }; it.play() }
                                pressed = false
                            }, contentAlignment = Alignment.Center) {
                                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = theme.top, modifier = Modifier.size(38.dp))
                            }
                            GlassButton(Icons.Default.SkipNext, "Next", theme.accent, glass, edge) { player?.seekToNextMediaItem() }
                            GlassButton(Icons.Default.Repeat, "Repeat", theme.accent, glass, edge) {
                                player?.repeatMode = if (player?.repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("PREVIOUS  •  PLAY  •  NEXT", color = Color.White.copy(alpha = .45f), fontSize = 9.sp, letterSpacing = 2.sp)
                    }
                    else -> {
                        GlassPanel(glass, edge) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text("YOUR LIBRARY", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                                    Text("${tracks.size} songs on this device", color = theme.accent, fontSize = 11.sp)
                                }
                                GlassButton(Icons.Default.Add, "Add songs", theme.accent, glass, edge) { picker.launch(arrayOf("audio/*")) }
                            }
                            Spacer(Modifier.height(12.dp))
                            if (tracks.isEmpty()) {
                                Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.LibraryMusic, null, tint = theme.accent, modifier = Modifier.size(48.dp))
                                        Spacer(Modifier.height(12.dp))
                                        Text("Your music will appear here", color = Color.White, fontWeight = FontWeight.SemiBold)
                                        Text("Tap + to choose audio files", color = Color.White.copy(alpha = .55f), fontSize = 12.sp)
                                    }
                                }
                            } else {
                                LazyColumn(Modifier.heightIn(max = 440.dp)) {
                                    itemsIndexed(tracks) { index, track ->
                                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable {
                                            selected = index
                                            player?.setMediaItems(tracks.map { t -> mediaItem(t) }, index, 0L)
                                            player?.prepare()
                                            player?.play()
                                            page = "player"
                                        }.padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                            AlbumThumb(track.artwork, theme.accent)
                                            Spacer(Modifier.width(12.dp))
                                            Text(track.name, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                                            if (index == selected) Icon(Icons.Default.GraphicEq, null, tint = theme.accent)
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        if (current != null) {
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(glass).border(1.dp, edge, RoundedCornerShape(22.dp)).clickable { page = "player" }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                AlbumThumb(current.artwork, theme.accent)
                                Spacer(Modifier.width(10.dp))
                                Text(current.name, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                                IconButton(onClick = { if (player?.isPlaying == true) player.pause() else player?.play() }) {
                                    Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = theme.accent)
                                }
                                IconButton(onClick = { player?.seekToNextMediaItem() }) { Icon(Icons.Default.SkipNext, null, tint = theme.accent) }
                            }
                        }
                    }
                }
                Spacer(Modifier.weight(1f, fill = false))
            }
        }
    }
}

@Composable
private fun GlassPanel(fill: Color, edge: Color, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color.White.copy(alpha = .13f), fill, Color.White.copy(alpha = .045f))), RoundedCornerShape(30.dp)).border(1.dp, edge.copy(alpha = .85f), RoundedCornerShape(30.dp)).padding(18.dp), content = content)
}
@Composable
private fun GlassButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, accent: Color, fill: Color, edge: Color, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).background(Brush.linearGradient(listOf(fill, Color.White.copy(alpha = .035f))), CircleShape).border(1.dp, edge, CircleShape).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = label, tint = accent, modifier = Modifier.size(21.dp))
    }
}
@Composable
private fun AlbumCover(data: ByteArray?, size: Int, accent: Color, fill: Color, edge: Color, modifier: Modifier = Modifier) {
    val bitmap = remember(data) { data?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
    Box(modifier.size(size.dp).background(Brush.linearGradient(listOf(fill, Color.White.copy(alpha = .04f))), RoundedCornerShape(34.dp)).border(1.dp, edge, RoundedCornerShape(34.dp)).padding(10.dp), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap, null, Modifier.fillMaxSize().clip(RoundedCornerShape(27.dp)))
        else Icon(Icons.Default.Album, null, tint = accent, modifier = Modifier.size(100.dp))
    }
}
@Composable
private fun AlbumThumb(data: ByteArray?, accent: Color) {
    val bitmap = remember(data) { data?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
    Box(Modifier.size(46.dp).background(Color.White.copy(alpha = .10f), RoundedCornerShape(12.dp)).border(1.dp, Color.White.copy(alpha = .18f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap, null, Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)))
        else Icon(Icons.Default.MusicNote, null, tint = accent)
    }
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}
