AutoGson
========

Usage
-----

* Clone into your AS project.
```bash
cd ~/ProjectDir
git clone git@github.com:alex-richards/AutoGson.git
```
* Include in `settings.gradle`.
```bash
echo "include ':AutoGson:AutoGson'" >> settings.gradle
```
* Add as an [apt dependency][1], or you'll pull in a load of garbage.
```gradle
dependencies{
  apt project(':AutoGson:AutoGson')
}
```
* Optionally mark any packages you need to restrict generation to in `package-info.java`.
```java
@AutoGson
package com.package.name;
import com.github.alexrichards.autogson.AutoGson;
```
* Add as a `TypeAdapterFactory` in `Gson`. _Be sure to import the correct version, `AutoGson` generates one for each package containing `@AutoValue` classes._
```java
return new GsonBuilder()
    .registerTypeAdapterFactory(new AutoGsonTypeAdapterFactory())
    .build();
```

[1]: https://bitbucket.org/hvisser/android-apt
