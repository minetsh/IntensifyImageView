# Android Picture Preview
一个用于大图预览的类库，可用于显示几十兆级别的图片，使图片在缩放过程中保持最佳清晰度。

|![单张图片的缩放加载](/screenshot/single_preview.gif)|![多张图片切换预览](/screenshot/multi_preview.gif)|![ScaleType切换](/screenshot/scale_type_preview.gif)|
|:---:|:---:|:---:|
|单图预览|多图切换|ScaleType|

# Demo
安装 [apk](https://www.pgyer.com/iimg) 文件预览效果，或者通过下面二维码去下载安装：

![demo apk 文件地址二维码](/screenshot/iimg.png)

# Usage

Use Gradle:

``` groovy
dependencies {
   compile 'me.kareluo.intensify:image:1.0.0'
}
```

Or Maven:

``` xml
<dependency>
  <groupId>me.kareluo.intensify</groupId>
  <artifactId>image</artifactId>
  <version>1.0.0</version>
  <type>pom</type>
</dependency>
```

# Sample

布局文件如下，width和height都要使用match_parent：

``` xml
<me.kareluo.intensify.image.IntensifyImageView
   android:id="@+id/intensify_image"
   android:layout_width="match_parent"
   android:layout_height="match_parent"
   app:scaleType="fitAuto" />
```

代码中可以通过以下三种方式设置图片资源：

``` java
IntensifyImageView imageView = (IntensifyImageView)findViewById(R.id.intensify_image);

// 通过流设置
imageView.setImage(InputStream inputStream);

// 通过文件设置
imageView.setImage(File file);

// 通过文件路径设置
imageView.setImage(String path);
```

# Blog
大图预览的主要原理是通过`BitmapRegionDecoder`对图片进行分块加载实现的，并在内存中维护不同精度，不同区域的Bitmap对象的加载与回收，详细内容可参考此篇博客：[Android 原图预览](http://kareluo.github.io/2015/12/27/Android-Picture-Preview/)

# License

``` license
Copyright 2015 kareluo.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

	http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

