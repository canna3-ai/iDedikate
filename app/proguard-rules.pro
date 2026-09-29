# Room instantiates generated *_Impl databases reflectively via their no-arg constructor.
# The Ads SDK pulls in room-runtime 2.2.5, whose consumer rule keeps these classes but not
# their constructors, so R8 strips WorkDatabase_Impl.<init>() and WorkManager crashes at startup.
-keep class * extends androidx.room.RoomDatabase {
    <init>();
}
