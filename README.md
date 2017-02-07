Introduction to maxmind-geoip2-scala
====================================

This is a simple Scala wrapper for the MaxMind GeoIP2-java library: http://maxmind.github.io/GeoIP2-java/
Note that the GeoIP2 is still in beta.

This project is based on the https://github.com/snowplow/scala-maxmind-geoip from Snowplow!

Installation
============

I suggest that you clone this repository and publish to local repository to be used in another project.

`sbt +publish-local`

After that, you can use it in your sbt by adding the following dependency:

`libraryDependencies += "com.sanoma.cda" %% "maxmind-geoip2-scala" % "1.5.4"`

You should also be able to generate a fat jar with Assembly.
We chose not to include the data file into the jar as you should update that from time to time.

Data
====
Download (and unzip) data from here:
http://dev.maxmind.com/geoip/geoip2/geolite2/
http://geolite.maxmind.com/download/geoip/database/GeoLite2-City.mmdb.gz

Running tests
=============
Before running tests download the GeoLite2-City.mmdb. There is a script in src/test/resources to help you in that (the db must be in src/test/resources). Then just run tests with:

`sbt +test`

Usage
=====

Here is a simple usage example:

```scala
import com.sanoma.cda.geoip.MaxMindIpGeo
val geoIp = MaxMindIpGeo("/data/MaxMind/GeoLite2-City.mmdb", 1000)
println(geoIp.getLocation("123.123.123.123"))
```

If you are going to use this in multithreaded environment (like Spark), then you'd want to use the threaded version:

```scala
val geoIp = MaxMindIpGeo("/data/MaxMind/GeoLite2-City.mmdb", 1000, synchronized = true)
```

If you know that the MaxMind Lite database has some problems in the areas that you are interested in, you can specify function that is used to filter the output. Here is an example for filtering out location field from the output:
NOTE: this API changed a little since 1.4.x - now you can define function that transforms the IpLocation to new one or none.

```scala
import com.sanoma.cda.geoip.MaxMindIpGeo
import com.sanoma.cda.geo.Point
import com.sanoma.cda.geoip.IpLocation
val removeIncorrectLatLong: MaxMindIpGeo.IpLocationFilter = loc => {
  val geoPointBlacklist = Set(Point(39.9289,116.3883)) // we "know" this is never correct
  loc.geoPoint match {
    // if we get a location, but it's on black list, we just remove it
    case Some(p) if geoPointBlacklist.contains(p) => Some(loc.copy(geoPoint = None))
    case _ => Some(loc)
  }
}
val geoIpWithoutFilter = MaxMindIpGeo("src/test/resources/GeoLite2-City.mmdb", 1000)
val geoIpWithFilter = MaxMindIpGeo("src/test/resources/GeoLite2-City.mmdb", 1000, postFilterIpLocation = removeIncorrectLatLong)

// now calling is exactly the same way
println(geoIpWithoutFilter.getLocation("123.123.123.123"))
println(geoIpWithFilter.getLocation("123.123.123.123"))
```

The postFilter is a function from IpLocation to Option[IpLocation] which means that you can also make it None if you believe that none of the information in it is correct.


Geo-package
===========

Version 1.2 introduces geo package that contains some geo primitives as well as some algorithms. This is the first stab at the APIs to see if they are usefull, not completely thought out yet - comments are wellcome.
The main motivation of these classes were to be able to do geo fencing to see if given point (latitude, longitude) from the MaxMind library falls inside some pre-defined area.
Unfortunately, this slightly changed the API of the IpLocation class. Namely the tuple that previously held latitude and longitude was changed in Point. There are implicit conversions available between Tuple2 and Point though.

The classes of the Geo package are simple. The design started out as having no direct relation to geo coordinates and worked with any coordinate system. The main use cases that we have include relatively small areas that are far from the data boundary or the poles. However, there are 2 distance functions calculating the distance between 2 points on earth. These were introduced for the circle class - which is defined by having a radius in meters around center point that is expected to be in degrees.

The GeoAreaMap is designed to hold the different geo areas, such as the circles, rectangles and simple polygons. It can give the ID of the are that the given point belongs to. Note that it will always search the areas in the same order - so remember to give the most probable areas in the beginning of the list. The data structure will not optimize this by itself.

Here is an example of doing lookup using GeoAreaMap

```scala
import com.sanoma.cda.geo._
val turku = Point(60.45, 22.25)
val helsinki = Point(60.17, 24.94)
val tamminiemi = Point(60.1892,24.8838)
val mantyniemi = Point(60.1844,24.8968)
val hCircle = Circle(helsinki, 3500) // 3.5km around Helsinki
val tCircle = Circle(tamminiemi, 1000)
val hRectangle = Rectangle(lowerLeft = (60.15, 24.84), upperRight = (60.20, 25.00))
val aPoly = Polygon(List((60.30, 24.88), (60.34, 24.95), (60.295, 25.02)))

val data = List("tamminiemi" -> tCircle, "helsinki" -> hCircle, "airport" -> aPoly, "hRect" -> hRectangle)
val gmap = GeoAreaMap.fromSeq(data)

gmap.get(turku) // None
gmap.get(mantyniemi) // Some("tamminiemi")
gmap.getAll(mantyniemi) // List(tamminiemi, helsinki, hRect)
```


Geohashing
==========
Geo-package now contains also basic Geohash encoding and decoding. For more information on Geohash, see https://en.wikipedia.org/wiki/Geohash and http://geohash.org/.

This is how you can use the geohashing functions
```scala
import com.sanoma.cda.geo._
Point(45.0,88.0).geoHash          // "tzyxfrzxuxgz"
Point(45.0,88.0).geoHash(5)       // "tzyxf"
Point.fromGeohash("tzyxfrzxu")    // Point(45.0,88.0)

import com.sanoma.cda.geo.GeoHash._
val p = Point(-53.876953125, -155.91796875)
val h = encode(p)    // 0w3j7zzzzzzz
val h6 = encode(p,6) // 0w3j7z
decode(h)       // Point(-53.8769532,-155.917969)
decode(h6)      // Point(-53.88,-155.92)
decodeFully(h6) // (-53.87969970703125,-155.9234619140625,0.00274658203125,0.0054931640625)
```

About the geohash implementation in this Scala library:
There are a few libraries for geohashing for different languages. Before this, there was no Scala package around, but there were a few Java-versions which could have been used. Unfortunately many of the packages gave slightly different answers when I tested them. Therefore I ended up writing scala version from scratch.
Unfortunately, Geohash doesn't seem to have any reliable reference implementation or pseudo code available. This package contains some tests against Geohash.org. After getting frustrated for not being able to match results from geohash.org, this code was mostly rewritten after one of the Python versions. There I noticed that Python and Scala round differently and thus concluded that some of the differences agains geohash.org are due different roundings. But as there is no reference, I chose to continue with the JVM rounding and adjusted the tests.
Also, it is notable that the geohash.org is clearly wrong in some cases. As an example, geohash.org decodes this http://geohash.org/u26r and http://geohash.org/u26q to the same coordinates, which is clearly wrong.

It also seems that they round coordinates probably wrongly or at least to the way the rounding is specified on the Wikipedia page. See this example:
```scala
// Geohash.org decodes "uuxz" to 72.0,45.0
val full = decodeFully("uuxz") // (71.630859375,44.82421875,0.087890625,0.17578125)
// latitude should be between these:
full._1 + full._3 // 71.71875
full._1 - full._3 // 71.54296875
// But latitude from geohash.org does not fall in that range 
```

However, having said all that, this version is also not fully tested. Please do your own testing and create issues if doesn't seem right.


Geo-privacy
===========
This library is built to make it easier to handle location information in your Scala apps. Location from IP addresses is really not that accurate at all. However,
we realize that you might want to use the geo-pakage with data that originates from GPS or another very accurate source. Location has special properties from
the privacy point of view. We want to be careful about it. Therefore the Geo-package now contains also some functions that can be used to obfuscate the location.
These do not at the moment directly link into the API, but are rather provided as tools for you to build your own privacy enabling processing.

Location privacy can be controlled by:
 * Regulatory strategies. Local legistation.
 * Privacy policies. Agreements between the user and the service provider.
 * Anonymity. Using pseudonyms or no user ID, even grouping with other people
 * Obfuscation. Reducing the quality of the location data
  * Spatial obfuscation
  * Temporal obfuscation


We suggest that you look up the local legislation on the subject of collecting and using location data. We also suggest that you create privacy policies that give a clear picture of what you are doing and why. Anonymity is easy to achieve by leaving the user ID out of the location data completely, or replacing it with pseudonym that is changing reasonably often and therefore preventing long history of accurate user data that can be exploited. However, in many use cases, the user ID must be sent (even for other reasons). Therefore we offer some tools here to build obfuscation by reducing the accuracy of the location data.

One way to reduce threats to location privacy is to degrade the location information. This can be done by deliberately making the measurements inaccurate either by time or location. You can do temporal obfuscation by not sending the data at the real time it happened or not sending all location samples that you have in possession. Both of these confuse the location tracking. However, again, there are use cases where this is not possible and it is desired that the location is sent in real-time when it happens and always when it happens.

This package provides some tools to implement a system to degrade the spatial resolution of the location data and at the same time keeping it useable. What is right for you depends on your use case. Some ways to do this is to take the original accurate location latitude and longitude and
 * discretize the location by rounding of the degrees
 * discretize the location using geohash (if you want to match the discretized location to geohash database for example)
 * add uniform noise with max offset of 1km to the location sample
 * add gaussian noise with for example std of 1km to the accurate location

```scala
import com.sanoma.cda.geo.GeoPrivacy._
import com.sanoma.cda.geo.Point

val p1 = Point(1.234567, 3.4567890)

discretize(2)(p1)
//res0: com.sanoma.cda.geo.Point = Point(1.23,3.46)

discretizeWithGeoHash(4)(p1)
//res1: com.sanoma.cda.geo.Point = Point(1.3,3.3)

val p2 = Point(1.2, 3.4)

additiveUniformNoise(0.1, 0.1)(p2)
//res2: com.sanoma.cda.geo.Point = Point(1.2785796832525629,3.497788928956041)

additiveGaussianNoise(0.1)(p2)
//res3: com.sanoma.cda.geo.Point = Point(1.1332416627810706,3.3081027046796434)

additiveUniformNoiseMeters(1000, 1000)(p2)
//res4: com.sanoma.cda.geo.Point = Point(1.1947079935372003,3.3969605065141217)

additiveGaussianNoiseMeters(1000)(p2)
//res5: com.sanoma.cda.geo.Point = Point(1.1945165899260433,3.3888928371192066)
```

There is also k-anonymity function. There you would return the smallest geohash that contains at least k-people in it. This is not implemented yet here.
Some considerations on this. We need to define a time period and do sliding windows over time. This costs memory as it would need to be calculated for multiple of the smallest
desired geohashes (that can then be combined).


Coordinate conversions
======================
There are now also functions for doing coordinate conversions between GPS coordinates and the Finnish EUREF-FIN projected coordinates (ETRS89-TM35FIN). 
THe coordinate conversions are not fully integrated into the rest of the library. The library has been very lightweight and does not try to be a full geo-library in any means. Therefore, the Point class for example, does not contain infomration about the coordinate system in it. And mostly you can use just tuples. This may be changed in the future.

This is how you do the coordinate conversions:
```scala
import com.sanoma.cda.geo.Point
import com.sanoma.cda.geo.CoordinateConversions._

val GPS = Point(60.1672065,24.943796)
// GPS: com.sanoma.cda.geo.Point = Point(60.1672065,24.943796)

val EUREF_FIN = wgs842etrs89tm35fin(GPS)
// EUREF_FIN: (Double, Double) = (6671809.459860587,385901.3059246596)

etrs89tm35fin2wgs84(EUREF_FIN)
// res0: com.sanoma.cda.geo.Point = Point(60.16720650000114,24.943796000000106)
```
 
