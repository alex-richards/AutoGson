AutoGson
========

Usage
-----

* Clone into your AS project.
```
cd ~/ProjectDir
git clone git@github.com:alex-richards/AutoGson.git
```
* Include in `settings.gradle`.
```
echo "include ':AutoGson:AutoGson'" >> settings.gradle
```
* Add as an [apt dependency][1], or you'll pull in a load of garbage.
```
dependencies{
  apt project(':AutoGson:AutoGson')
}
```
* Add as a `TypeAdapterFactory` in `Gson`. _Be sure to import the correct version, `AutoGson` generates one for each package containing `@AutoValue` classes._
```
return new GsonBuilder()
    .registerTypeAdapterFactory(new AutoGsonTypeAdapterFactory())
    .build();
```

// TODO
-------

* Package properly and put it in a repo somewhere.

[1]: https://bitbucket.org/hvisser/android-apt
