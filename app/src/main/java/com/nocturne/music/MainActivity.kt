package com.nocturne.music

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import org.json.JSONArray
import java.io.File
import java.util.Locale

class MainActivity : Activity() {
 data class Song(val id: Long, val uri: Uri, var title: String, var artist: String, var album: String, var genre: String, var year: String, var track: String, val duration: Long, val size: Long)
 private val songs = mutableListOf<Song>()
 private val queue = mutableListOf<Song>()
 private val favorites = mutableSetOf<String>()
 private var current: Song? = null
 private var player: MediaPlayer? = null
 private var shuffle = false
 private var repeat = 0
 private var sort = 0
 private var onlyFavorites = false
 private var themeIndex = 0
 private var query = ""
 private lateinit var root: LinearLayout
 private lateinit var list: LinearLayout
 private lateinit var playButton: TextView
 private lateinit var progress: SeekBar
 private lateinit var nowLabel: TextView
 private val handler = Handler(Looper.getMainLooper())
 private val themes = intArrayOf(0xFFBB9AFF.toInt(),0xFF64D9C0.toInt(),0xFFFFB86B.toInt(),0xFFEC91B6.toInt())
 private val bg = 0xFF14121B.toInt()
 private val card = 0xFF24212E.toInt()
 private val white = 0xFFF5F1FC.toInt()
 private val muted = 0xFFB5ADBF.toInt()
 private val prefs by lazy { getSharedPreferences("nocturne", MODE_PRIVATE) }
 private fun key(s: Song) = "${s.title.trim().lowercase(Locale.ROOT)}|${s.artist.trim().lowercase(Locale.ROOT)}|${s.album.trim().lowercase(Locale.ROOT)}|${s.duration}"
 private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
 private fun shape(color: Int, radius: Int = 18) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
 private fun text(s: String, size: Float = 15f, color: Int = white) = TextView(this).apply { text=s; textSize=size; setTextColor(color); setPadding(dp(8),dp(8),dp(8),dp(8)) }
 private fun button(s: String, action: () -> Unit) = text(s,16f,themes[themeIndex]).apply { setOnClickListener { action() }; gravity=Gravity.CENTER }
 private fun row() = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
 private fun column() = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
 private fun LinearLayout.add(v: View, weight: Float = 0f) { addView(v, if (orientation==LinearLayout.HORIZONTAL) LinearLayout.LayoutParams(if(weight>0) 0 else ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT,weight) else LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT)) }
 override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState)
  themeIndex=prefs.getInt("theme",0).coerceIn(0,3); sort=prefs.getInt("sort",0); shuffle=prefs.getBoolean("shuffle",false); repeat=prefs.getInt("repeat",0)
  prefs.getStringSet("favorites", emptySet())?.let { favorites.addAll(it) }
  render(); requestAudio()
  handler.post(object:Runnable { override fun run() { if(::progress.isInitialized && player?.isPlaying==true) { progress.max=player?.duration?:0; progress.progress=player?.currentPosition?:0 }; handler.postDelayed(this,500) } })
 }
 private fun requestAudio() { val p=if(Build.VERSION.SDK_INT>=33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
  if(checkSelfPermission(p)!=PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(p),101) else scan()
 }
 override fun onRequestPermissionsResult(requestCode:Int, permissions:Array<out String>, grantResults:IntArray) { super.onRequestPermissionsResult(requestCode,permissions,grantResults); if(requestCode==101 && grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED) scan() else Toast.makeText(this,"Разрешите доступ к аудиофайлам в настройках",Toast.LENGTH_LONG).show() }
 private fun scan() { songs.clear()
  val projection=arrayOf(MediaStore.Audio.Media._ID,MediaStore.Audio.Media.TITLE,MediaStore.Audio.Media.ARTIST,MediaStore.Audio.Media.ALBUM,MediaStore.Audio.Media.DURATION,MediaStore.Audio.Media.SIZE,MediaStore.Audio.Media.MIME_TYPE)
  contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,projection,"${MediaStore.Audio.Media.IS_MUSIC} != 0",null,null)?.use { c ->
   while(c.moveToNext()) { val mime=c.getString(6)?:""; if(mime!="audio/mpeg" && mime!="audio/mp3" && mime!="audio/x-mpeg") continue
    val id=c.getLong(0); val uri=ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,id)
    songs.add(Song(id,uri,c.getString(1)?:"Без названия",c.getString(2)?:"Неизвестный исполнитель",c.getString(3)?:"Неизвестный альбом","","","",c.getLong(4),c.getLong(5)))
   }
  }; render()
 }
 private fun artwork(s:Song): android.graphics.Bitmap? = try { val m=MediaMetadataRetriever(); m.setDataSource(this,s.uri); val bytes=m.embeddedPicture; m.release(); bytes?.let { BitmapFactory.decodeByteArray(it,0,it.size) } } catch(_:Exception){null}
 private fun render() {
  root=column().apply { setBackgroundColor(bg); setPadding(dp(12),dp(20),dp(12),dp(8)) }; setContentView(root)
  val heading=row(); val title=text("◉  NOCTURNE",24f,themes[themeIndex]).apply { setTypeface(null,Typeface.BOLD) }; heading.add(title,1f)
  heading.add(button("◷") { chooseSort() }); heading.add(button("◈") { chooseTheme() }); heading.add(button("⋮") { menu() }); root.add(heading)
  val search=EditText(this).apply { setSingleLine(true); hint="Поиск по медиатеке"; setHintTextColor(muted); setTextColor(white); background=shape(card); setPadding(dp(16),dp(8),dp(16),dp(8)); setText(query); addTextChangedListener(object:android.text.TextWatcher { override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}; override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){ query=s.toString(); populate() }; override fun afterTextChanged(s:android.text.Editable?){} }) }
  root.add(search)
  val tabs=row(); tabs.add(button(if(onlyFavorites) "Все треки" else "Все треки ✓") { onlyFavorites=false; render() },1f); tabs.add(button(if(onlyFavorites) "♥ Избранное ✓" else "♡ Избранное") { onlyFavorites=true; render() },1f); tabs.add(button("↻ Обновить") { scan() }); root.add(tabs)
  val scroll=ScrollView(this); list=column(); scroll.addView(list); root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
  val playerPanel=column().apply { background=shape(card); setPadding(dp(10),dp(8),dp(10),dp(8)) }
  nowLabel=text("Ничего не играет",14f,white).apply { maxLines=2 }; playerPanel.add(nowLabel)
  progress=SeekBar(this).apply { progressTintList=android.content.res.ColorStateList.valueOf(themes[themeIndex]); setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener { override fun onStartTrackingTouch(s:SeekBar?){}; override fun onStopTrackingTouch(s:SeekBar?){ player?.seekTo(progress.progress) }; override fun onProgressChanged(s:SeekBar?,p:Int,fromUser:Boolean){} }) }; playerPanel.add(progress)
  val controls=row(); controls.add(button(if(shuffle) "🔀" else "⇄") { shuffle=!shuffle; prefs.edit().putBoolean("shuffle",shuffle).apply(); render() },1f)
  controls.add(button("|◀") { previous() },1f); playButton=button(if(player?.isPlaying==true) "Ⅱ" else "▶") { toggle() }; controls.add(playButton,1f)
  controls.add(button("▶|") { next() },1f); controls.add(button(arrayOf("↪","↻","↻¹")[repeat]) { repeat=(repeat+1)%3; prefs.edit().putInt("repeat",repeat).apply(); render() },1f)
  playerPanel.add(controls); root.add(playerPanel); updateNow(); populate()
 }
 private fun filtered():List<Song> { val q=query.trim().lowercase(Locale.ROOT); val base=songs.filter { (!onlyFavorites||favorites.contains(key(it))) && (q.isEmpty()||listOf(it.title,it.artist,it.album).any { v->v.lowercase(Locale.ROOT).contains(q) }) }
  return when(sort){1->base.sortedBy { it.artist.lowercase(Locale.ROOT) };2->base.sortedBy { it.album.lowercase(Locale.ROOT) };3->base.sortedByDescending { it.duration };4->base.sortedBy { it.duration };else->base.sortedBy { it.title.lowercase(Locale.ROOT) } }
 }
 private fun populate() { if(!::list.isInitialized)return; list.removeAllViews(); val entries=filtered(); if(entries.isEmpty()){list.add(text("Музыка не найдена. Проверьте доступ к аудиофайлам.",15f,muted));return}
  entries.forEach { s -> val item=row().apply { background=shape(card); setPadding(dp(4),dp(4),dp(4),dp(4)) }
   val art=ImageView(this).apply { layoutParams=LinearLayout.LayoutParams(dp(56),dp(56)); scaleType=ImageView.ScaleType.CENTER_CROP; val bitmap=artwork(s); if(bitmap!=null) setImageBitmap(bitmap) else setImageResource(com.nocturne.music.R.drawable.ic_launcher) }; item.addView(art)
   val info=column(); info.add(text(s.title,15f)); info.add(text("${s.artist} · ${s.album}",12f,muted).apply { maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END }); item.add(info,1f)
   item.add(button(if(favorites.contains(key(s))) "♥" else "♡") { val k=key(s); if(!favorites.add(k))favorites.remove(k); saveFavorites(); populate() })
   item.add(button("⋮") { songMenu(s) }); item.setOnClickListener { play(s) }; list.add(item)
   val space=View(this); list.addView(space,LinearLayout.LayoutParams(1,dp(6)))
  }
 }
 private fun updateNow(){ if(!::nowLabel.isInitialized)return; nowLabel.text=current?.let { "${it.title}  ·  ${it.artist}" }?:"Ничего не играет"; if(::playButton.isInitialized) playButton.text=if(player?.isPlaying==true) "Ⅱ" else "▶" }
 private fun play(s:Song) { try { player?.release(); player=MediaPlayer().apply { setDataSource(this@MainActivity,s.uri); prepare(); setOnCompletionListener { if(repeat==2) {seekTo(0);start()} else next() }; start() }; current=s; if(queue.none { it.id==s.id }) { queue.clear();queue.addAll(filtered()) }; updateNow() } catch(e:Exception){ Toast.makeText(this,"Ошибка воспроизведения: ${e.message}",Toast.LENGTH_LONG).show() } }
 private fun toggle(){ if(player==null) { filtered().firstOrNull()?.let { play(it) };return }; if(player?.isPlaying==true)player?.pause() else player?.start();updateNow() }
 private fun next(){ val currentIndex=queue.indexOfFirst { it.id==current?.id }; if(queue.isEmpty()){filtered().firstOrNull()?.let { play(it) };return}; if(shuffle){play(queue.random());return}; if(currentIndex+1<queue.size) play(queue[currentIndex+1]) else if(repeat==1) play(queue.first()) else { player?.pause();updateNow() } }
 private fun previous(){ if(queue.isEmpty())return; val index=queue.indexOfFirst { it.id==current?.id }; play(queue[if(index<=0)queue.lastIndex else index-1]) }
 private fun songMenu(s:Song){ val actions=arrayOf("Воспроизвести следующей","Добавить в очередь","Редактировать теги","Удалить файл","Сведения")
  android.app.AlertDialog.Builder(this).setTitle(s.title).setItems(actions){_,which->when(which){0->{queue.removeAll{it.id==s.id};val i=queue.indexOfFirst{it.id==current?.id};queue.add((i+1).coerceAtLeast(0).coerceAtMost(queue.size),s)};1->queue.add(s);2->edit(s);3->delete(s);4->android.app.AlertDialog.Builder(this).setMessage("${s.artist}\n${s.album}\n${s.duration/1000} сек\n${s.size/1024} КБ\n${s.uri}").setPositiveButton("OK",null).show()}}.show()
 }
 private fun edit(s:Song){ val fields=listOf("Название","Исполнитель","Альбом","Жанр","Год","Номер трека")
  val values=listOf(s.title,s.artist,s.album,s.genre,s.year,s.track); val layout=column().apply { setPadding(dp(16),dp(4),dp(16),0) }; val inputs=mutableListOf<EditText>()
  fields.forEachIndexed { index,label -> layout.add(text(label,13f,muted)); val input:EditText=if(index==1||index==2){ val suggestions=songs.map { if(index==1)it.artist else it.album }.filter { it.isNotBlank() }.distinct().sorted(); AutoCompleteTextView(this).apply { threshold=1; setAdapter(ArrayAdapter(this@MainActivity,android.R.layout.simple_dropdown_item_1line,suggestions)); setOnFocusChangeListener { v,focus->if(focus)(v as AutoCompleteTextView).showDropDown() } } }else EditText(this)
   input.setSingleLine(true);input.setText(values[index]);input.setTextColor(white);input.textSize=16f;layout.add(input);inputs.add(input)
  }
  val scroller=ScrollView(this).apply { addView(layout) }
  android.app.AlertDialog.Builder(this).setTitle("Редактирование MP3").setView(scroller).setNegativeButton("Отмена",null).setPositiveButton("Сохранить"){_,_->
   val oldKey=key(s); val newValues=inputs.map { it.text.toString().trim() }; if(newValues[0].isBlank()){Toast.makeText(this,"Название не может быть пустым",Toast.LENGTH_SHORT).show();return@setPositiveButton}
   val changes=mapOf("TIT2" to newValues[0],"TPE1" to newValues[1],"TALB" to newValues[2],"TCON" to newValues[3],"TYER" to newValues[4],"TRCK" to newValues[5]); requestWrite(s.uri) {
    try { Id3Editor.write(this,s.uri,changes); s.title=newValues[0];s.artist=newValues[1];s.album=newValues[2];s.genre=newValues[3];s.year=newValues[4];s.track=newValues[5]; if(favorites.remove(oldKey))favorites.add(key(s));saveFavorites();scan();Toast.makeText(this,"Теги сохранены",Toast.LENGTH_SHORT).show() }catch(e:Exception){Toast.makeText(this,"Ошибка записи: ${e.message}",Toast.LENGTH_LONG).show()}
   }
  }.show()
 }
 private var pending: (() -> Unit)?=null
 private fun requestWrite(uri:Uri, after:()->Unit){ if(Build.VERSION.SDK_INT<30){Toast.makeText(this,"Редактирование файлов требует Android 11 или новее",Toast.LENGTH_LONG).show();return}; pending=after; try { val sender=MediaStore.createWriteRequest(contentResolver,listOf(uri)).intentSender; startIntentSenderForResult(sender,201,null,0,0,0) }catch(e:Exception){ pending=null;Toast.makeText(this,"Нет доступа к записи: ${e.message}",Toast.LENGTH_LONG).show() } }
 private fun delete(s:Song){ if(Build.VERSION.SDK_INT<30){Toast.makeText(this,"Удаление файлов требует Android 11 или новее",Toast.LENGTH_LONG).show();return}; android.app.AlertDialog.Builder(this).setTitle("Удалить файл?").setMessage("${s.title}\nФайл будет удалён из памяти телефона.").setNegativeButton("Отмена",null).setPositiveButton("Удалить"){_,_->try { val sender=MediaStore.createDeleteRequest(contentResolver,listOf(s.uri)).intentSender; startIntentSenderForResult(sender,202,null,0,0,0) }catch(e:Exception){Toast.makeText(this,e.message,Toast.LENGTH_LONG).show()} }.show() }
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data); if(requestCode==201){val action=pending;pending=null;if(resultCode==RESULT_OK)action?.invoke()};if(requestCode==202&&resultCode==RESULT_OK)scan();if(requestCode==301&&resultCode==RESULT_OK)data?.data?.let{exportFavorites(it)};if(requestCode==302&&resultCode==RESULT_OK)data?.data?.let{importFavorites(it)} }
 private fun chooseSort(){ val names=arrayOf("Название","Исполнитель","Альбом","Длительность ↓","Длительность ↑");android.app.AlertDialog.Builder(this).setTitle("Сортировка").setSingleChoiceItems(names,sort){d,i->sort=i;prefs.edit().putInt("sort",i).apply();d.dismiss();populate()}.show() }
 private fun chooseTheme(){val names=arrayOf("Аметист","Мята","Янтарь","Роза");android.app.AlertDialog.Builder(this).setTitle("Акцентный цвет").setSingleChoiceItems(names,themeIndex){d,i->themeIndex=i;prefs.edit().putInt("theme",i).apply();d.dismiss();render()}.show() }
 private fun menu(){ val names=arrayOf("Экспорт избранного (JSON)","Импорт избранного (JSON)","Очередь воспроизведения","О приложении");android.app.AlertDialog.Builder(this).setItems(names){_,i->when(i){0->{val intent=Intent(Intent.ACTION_CREATE_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="application/json";putExtra(Intent.EXTRA_TITLE,"nocturne-favorites.json")};startActivityForResult(intent,301)};1->{val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="application/json"};startActivityForResult(intent,302)};2->showQueue();3->android.app.AlertDialog.Builder(this).setMessage("Nocturne Music 0.1\nЛокальный MP3-плеер. Без рекламы и интернета.\nРезервное копирование избранного: меню → Экспорт.").setPositiveButton("OK",null).show()}}.show() }
 private fun showQueue(){ val names=queue.mapIndexed { i,s->"${i+1}. ${s.title} — ${s.artist}" }.toTypedArray();android.app.AlertDialog.Builder(this).setTitle("Очередь (нажмите, чтобы воспроизвести)").setItems(names){_,i->play(queue[i])}.setNeutralButton("Очистить"){_,_->queue.clear()}.setPositiveButton("Закрыть",null).show() }
 private fun saveFavorites(){prefs.edit().putStringSet("favorites",favorites.toSet()).apply()}
 private fun exportFavorites(uri:Uri){try{contentResolver.openOutputStream(uri)?.use{it.write(JSONArray(favorites.toList()).toString(2).toByteArray(Charsets.UTF_8))};Toast.makeText(this,"Резервная копия сохранена",Toast.LENGTH_SHORT).show()}catch(e:Exception){Toast.makeText(this,e.message,Toast.LENGTH_LONG).show()} }
 private fun importFavorites(uri:Uri){try{val json=contentResolver.openInputStream(uri)?.bufferedReader()?.use{it.readText()}?:return;val array=JSONArray(json);for(i in 0 until array.length())favorites.add(array.getString(i));saveFavorites();populate();Toast.makeText(this,"Избранное восстановлено",Toast.LENGTH_SHORT).show()}catch(e:Exception){Toast.makeText(this,"Неверный файл: ${e.message}",Toast.LENGTH_LONG).show()} }
 override fun onDestroy(){player?.release();super.onDestroy()}
}
