package com.aquamusic.player

import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

class MainActivity : ComponentActivity() {
    private var controller by mutableStateOf<MediaController?>(null)
    private var controllerFuture: ListenableFuture<MediaController>? = null

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, MusicService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            runCatching { controller = future.get() }
        }, MoreExecutors.directExecutor())
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
fun AquaScreen(player: MediaController?) {
    var tracks by remember { mutableStateOf(listOf<Uri>()) }
    var selected by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val pulse by animateFloatAsState(if (pressed) .94f else 1f, label = "waterTap")
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { picked ->
        if (picked.isNotEmpty()) {
            picked.forEach { uri ->
                runCatching { androidx.compose.ui.platform.LocalContext.current }
                player?.addMediaItem(MediaItem.fromUri(uri))
            }
            tracks = (tracks + picked).distinct()
            if (player != null && player.mediaItemCount > 0) player.prepare()
        }
    }
    val bg = Brush.linearGradient(listOf(Color(0xFF071521), Color(0xFF102D43), Color(0xFF07111E)))
    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(Modifier.fillMaxSize().background(bg).padding(20.dp)) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("AQUA", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                        Text("MUSIC PLAYER", color = Color(0xFF79DDF5), fontSize = 10.sp, letterSpacing = 3.sp)
                    }
                    GlassIcon(Icons.Default.FolderOpen, "Choose music") { picker.launch(arrayOf("audio/*")) }
                }
                Spacer(Modifier.height(28.dp))
                Box(
                    Modifier.size(250.dp).background(
                        Brush.radialGradient(listOf(Color(0xFF3DD9F2).copy(alpha = .38f), Color(0xFF477BBD).copy(alpha = .13f), Color.Transparent)),
                        CircleShape
                    ).border(1.dp, Color.White.copy(alpha = .18f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(205.dp).background(Brush.linearGradient(listOf(Color(0xFF6CEBFF).copy(alpha = .24f), Color(0xFF536B9C).copy(alpha = .18f))), RoundedCornerShape(38.dp)).border(1.dp, Color.White.copy(alpha = .24f), RoundedCornerShape(38.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.GraphicEq, null, tint = Color(0xFF9CEFFF), modifier = Modifier.size(92.dp))
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(if (tracks.isEmpty()) "Your sound, your space" else "Track ${selected + 1} of ${tracks.size}", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                Text(if (tracks.isEmpty()) "Choose songs from your device" else tracks.getOrNull(selected)?.lastPathSegment ?: "Selected audio", color = Color(0xFF9DB7C8), fontSize = 13.sp, maxLines = 1)
                Spacer(Modifier.height(22.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    GlassIcon(Icons.Default.Shuffle, "Shuffle") { player?.shuffleModeEnabled = !(player?.shuffleModeEnabled ?: false) }
                    GlassIcon(Icons.Default.SkipPrevious, "Previous", 52) { player?.seekToPreviousMediaItem() }
                    Box(Modifier.size(72.dp).scale(pulse).background(Brush.linearGradient(listOf(Color(0xFFB6F7FF), Color(0xFF43B9E5))), CircleShape).clickable {
                        pressed = true
                        if (player != null) {
                            if (player.isPlaying) player.pause()
                            else {
                                if (player.mediaItemCount == 0 && tracks.isNotEmpty()) {
                                    player.setMediaItems(tracks.map { MediaItem.fromUri(it) })
                                    player.prepare()
                                }
                                player.play()
                            }
                            playing = player.isPlaying
                        } else playing = !playing
                        pressed = false
                    }, contentAlignment = Alignment.Center) {
                        Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color(0xFF082033), modifier = Modifier.size(40.dp))
                    }
                    GlassIcon(Icons.Default.SkipNext, "Next", 52) { player?.seekToNextMediaItem() }
                    GlassIcon(Icons.Default.Repeat, "Repeat") {
                        player?.repeatMode = if (player?.repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
                    }
                }
                Spacer(Modifier.height(22.dp))
                GlassPanel {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("YOUR LIBRARY", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Text("${tracks.size} songs", color = Color(0xFF8EDFF3))
                    }
                    Spacer(Modifier.height(8.dp))
                    if (tracks.isEmpty()) Text("Tap the folder icon to add audio files.", color = Color(0xFF9DB7C8), fontSize = 13.sp)
                    else LazyColumn(Modifier.heightIn(max = 170.dp)) {
                        itemsIndexed(tracks) { index, uri ->
                            Row(Modifier.fillMaxWidth().clickable {
                                selected = index
                                player?.let {
                                    it.setMediaItem(MediaItem.fromUri(uri))
                                    it.prepare()
                                    it.play()
                                    playing = true
                                }
                            }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.MusicNote, null, tint = Color(0xFF78DDF2))
                                Spacer(Modifier.width(10.dp))
                                Text(uri.lastPathSegment ?: "Audio file", color = Color.White, modifier = Modifier.weight(1f), maxLines = 1)
                                if (index == selected) Icon(Icons.Default.GraphicEq, null, tint = Color(0xFF78DDF2))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { picker.launch(arrayOf("audio/*")) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF9DEEFF))) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("ADD MUSIC FILES")
                    }
                }
                Spacer(Modifier.weight(1f))
                Text("LIQUID GLASS • MADE FOR MUSIC", color = Color(0xFF62879B), fontSize = 10.sp, letterSpacing = 2.sp)
            }
        }
    }
}

@Composable
fun GlassPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha = .075f), RoundedCornerShape(26.dp)).border(1.dp, Color.White.copy(alpha = .15f), RoundedCornerShape(26.dp)).padding(18.dp), content = content)
}

@Composable
fun GlassIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, size: Int = 44, onClick: () -> Unit) {
    Box(Modifier.size(size.dp).background(Brush.linearGradient(listOf(Color.White.copy(alpha = .18f), Color.White.copy(alpha = .045f))), CircleShape).border(1.dp, Color.White.copy(alpha = .2f), CircleShape).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = label, tint = Color(0xFFBCEFFF), modifier = Modifier.size((size * .48f).dp))
    }
}
