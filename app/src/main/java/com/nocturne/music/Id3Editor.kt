package com.nocturne.music

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

/** Writes ID3v2.3 text frames while preserving ordinary existing frames, including APIC artwork.
 * Works with unprotected MP3 files accessible through MediaStore write grants.
 */
object Id3Editor {
 private fun int32(b:ByteArray,p:Int):Int = ((b[p].toInt() and 255) shl 24) or ((b[p+1].toInt() and 255) shl 16) or ((b[p+2].toInt() and 255) shl 8) or (b[p+3].toInt() and 255)
 private fun sync(b:ByteArray,p:Int):Int = ((b[p].toInt() and 127) shl 21) or ((b[p+1].toInt() and 127) shl 14) or ((b[p+2].toInt() and 127) shl 7) or (b[p+3].toInt() and 127)
 private fun synBytes(n:Int)=byteArrayOf(((n shr 21) and 127).toByte(),((n shr 14) and 127).toByte(),((n shr 7) and 127).toByte(),(n and 127).toByte())
 private fun frame(id:String, bytes:ByteArray):ByteArray { val out=ByteArrayOutputStream();out.write(id.toByteArray(Charsets.US_ASCII));out.write(byteArrayOf((bytes.size shr 24).toByte(),(bytes.size shr 16).toByte(),(bytes.size shr 8).toByte(),bytes.size.toByte(),0,0));out.write(bytes);return out.toByteArray() }
 private fun copy(input:InputStream,out:FileOutputStream){val b=ByteArray(65536);while(true){val n=input.read(b);if(n<0)break;out.write(b,0,n)}}
 fun write(context:Context,uri:Uri,updates:Map<String,String>){
  val original=File.createTempFile("nocturne-original-",".mp3",context.cacheDir)
  val rewritten=File.createTempFile("nocturne-edited-",".mp3",context.cacheDir)
  try {
   context.contentResolver.openInputStream(uri)?.use { input->FileOutputStream(original).use{copy(input,it)} }?:error("Невозможно прочитать файл")
   var offset=0L;val preserved=ByteArrayOutputStream()
   FileInputStream(original).use { input ->
    val head=ByteArray(10);if(input.read(head)==10 && head.copyOfRange(0,3).contentEquals("ID3".toByteArray())){
     val version=head[3].toInt() and 255;val size=sync(head,6);offset=10L+size+if((head[5].toInt() and 16)!=0)10 else 0
     if(size in 0..(32*1024*1024) && version in 3..4 && (head[5].toInt() and 0xC0)==0){
      val tag=ByteArray(size);var read=0;while(read<size){val n=input.read(tag,read,size-read);if(n<0)break;read+=n}
      var pos=0
      // Skip extended header if present.
      if((head[5].toInt() and 0x40)!=0 && read>=4){val ext=if(version==4)sync(tag,0) else int32(tag,0)+4;pos=ext.coerceIn(0,read)}
      while(pos+10<=read){val id=String(tag,pos,4,Charsets.US_ASCII);if(!id.matches(Regex("[A-Z0-9]{4}")))break
       val length=if(version==4)sync(tag,pos+4) else int32(tag,pos+4);if(length<0||pos+10L+length>read)break
       val flags1=tag[pos+8].toInt() and 255;val flags2=tag[pos+9].toInt() and 255
       // Preserve only normal, uncompressed frames; APIC and unrelated text remain intact.
       if(!updates.containsKey(id) && flags1==0 && flags2==0)preserved.write(frame(id,tag.copyOfRange(pos+10,pos+10+length)))
       pos+=10+length
      }
     } else if(size>32*1024*1024)error("Слишком большой ID3-тег")
    }
   }
   val frames=ByteArrayOutputStream();frames.write(preserved.toByteArray())
   updates.forEach { (id,value)-> if(value.isNotBlank()) { val encoded=byteArrayOf(1)+value.toByteArray(Charsets.UTF_16);frames.write(frame(id,encoded)) } }
   val payload=frames.toByteArray();if(payload.size>=0x0FFFFFFF)error("Слишком большой ID3-тег")
   FileOutputStream(rewritten).use { output->output.write("ID3".toByteArray(Charsets.US_ASCII));output.write(byteArrayOf(3,0,0));output.write(synBytes(payload.size));output.write(payload)
    FileInputStream(original).use { input->var remaining=offset;while(remaining>0){val skipped=input.skip(remaining);if(skipped<=0)error("Повреждённый MP3-файл");remaining-=skipped};copy(input,output) }
    output.fd.sync()
   }
   // Copy only after the complete replacement is safely staged in the app cache.
   context.contentResolver.openFileDescriptor(uri,"rw")?.use { descriptor->FileOutputStream(descriptor.fileDescriptor).use { output -> FileInputStream(rewritten).use { input->copy(input,output) };output.channel.truncate(rewritten.length());output.fd.sync() } }?:error("Нет разрешения на запись")
  }finally{original.delete();rewritten.delete()}
 }
}
