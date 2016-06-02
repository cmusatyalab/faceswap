#############################################################################
# remove logging, note that this removes ALL logging, including the
# Log.e statements
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
