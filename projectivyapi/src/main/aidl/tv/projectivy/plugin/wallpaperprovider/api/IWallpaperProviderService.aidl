// IWallpaperProviderService.aidl
package tv.projectivy.plugin.wallpaperprovider.api;

import tv.projectivy.plugin.wallpaperprovider.api.Wallpaper;
import tv.projectivy.plugin.wallpaperprovider.api.Event;

interface IWallpaperProviderService {
    List<Wallpaper> getWallpapers(in Event event);
    String getPreferences();
    void setPreferences(String params);
}