//
//  MusicControls.m
//
//
//  Created by Juan Gonzalez on 12/16/16.
//  Updated by Gaven Henry on 11/7/17 for iOS 11 compatibility & new features
//  Updated by Eugene Cross on 14/10/19 for iOS 13 compatibility
//
//

#import "MusicControls.h"
#import "MusicControlsInfo.h"

//save the passed in info globally so we can configure the enabled/disabled commands and skip intervals
MusicControlsInfo * musicControlsSettings;
BOOL _commandCenterRegistered;

@implementation MusicControls

- (void) pluginInitialize
{
    NSLog(@"MusicControls.execute=plugininitialize");
    _commandCenterRegistered = NO;
}

- (void) create: (CDVInvokedUrlCommand *) command {
[self.commandDelegate runInBackground:^{

    NSDictionary * musicControlsInfoDict = [command.arguments objectAtIndex:0];
    MusicControlsInfo * musicControlsInfo = [[MusicControlsInfo alloc] initWithDictionary:musicControlsInfoDict];
    musicControlsSettings = musicControlsInfo;
    
        if (!NSClassFromString(@"MPNowPlayingInfoCenter")) { return; }
        
        MPNowPlayingInfoCenter * nowPlayingCenter =  [MPNowPlayingInfoCenter defaultCenter];
        NSDictionary * nowPlayingInfo = nowPlayingCenter.nowPlayingInfo;
        NSMutableDictionary * updatedNowPlayingInfo = [NSMutableDictionary dictionaryWithDictionary:nowPlayingInfo];
        
        MPMediaItemArtwork * mediaItemArtwork = [self createCoverArtwork:[musicControlsInfo cover]];
        if (mediaItemArtwork != nil) {
            [updatedNowPlayingInfo setObject:mediaItemArtwork forKey:MPMediaItemPropertyArtwork];
        }
        
        updatedNowPlayingInfo[MPMediaItemPropertyArtist] = musicControlsInfo.artist;
        updatedNowPlayingInfo[MPMediaItemPropertyAlbumTitle] = musicControlsInfo.album;
        updatedNowPlayingInfo[MPMediaItemPropertyTitle] = musicControlsInfo.track;
        updatedNowPlayingInfo[MPMediaItemPropertyPlaybackDuration] = [NSNumber numberWithFloat:[musicControlsInfo duration]];
        updatedNowPlayingInfo[MPNowPlayingInfoPropertyElapsedPlaybackTime] =[NSNumber numberWithFloat:[musicControlsInfo elapsed]];
        updatedNowPlayingInfo[MPNowPlayingInfoPropertyPlaybackRate] = [NSNumber numberWithBool:[musicControlsInfo isPlaying]];
        
        nowPlayingCenter.nowPlayingInfo = updatedNowPlayingInfo;
}];

[self registerMusicControlsEventListener];
}

- (void) updateIsPlaying: (CDVInvokedUrlCommand *) command {
    [self.commandDelegate runInBackground:^{
        NSDictionary * musicControlsInfoDict = [command.arguments objectAtIndex:0];
        MusicControlsInfo * musicControlsInfo = [[MusicControlsInfo alloc] initWithDictionary:musicControlsInfoDict];
        NSNumber * elapsed = [NSNumber numberWithDouble:[musicControlsInfo elapsed]];
        NSNumber * playbackRate = [NSNumber numberWithBool:[musicControlsInfo isPlaying]];
        

        MPNowPlayingInfoCenter * nowPlayingCenter = [MPNowPlayingInfoCenter defaultCenter];
        NSMutableDictionary * updatedNowPlayingInfo = [NSMutableDictionary dictionaryWithDictionary:nowPlayingCenter.nowPlayingInfo];

        [updatedNowPlayingInfo setObject:elapsed forKey:MPNowPlayingInfoPropertyElapsedPlaybackTime];
        [updatedNowPlayingInfo setObject:playbackRate forKey:MPNowPlayingInfoPropertyPlaybackRate];
        nowPlayingCenter.nowPlayingInfo = updatedNowPlayingInfo;
    }];
}

// this was performing the full function of updateIsPlaying and just adding elapsed time update as well
// moved the elapsed update into updateIsPlaying and made this just pass through to reduce code duplication
- (void) updateElapsed: (CDVInvokedUrlCommand *) command {
    [self.commandDelegate runInBackground:^{
            [self updateIsPlaying:(command)];
    }];
}

- (void) destroy: (CDVInvokedUrlCommand *) command {
    [self deregisterMusicControlsEventListener];
}

- (void) watch: (CDVInvokedUrlCommand *) command {
    [self.commandDelegate runInBackground:^{
        [self setLatestEventCallbackId:command.callbackId];
    }];
}

- (void) disablePlayCommand: (CDVInvokedUrlCommand *) command {
    MPRemoteCommandCenter *commandCenter = [MPRemoteCommandCenter sharedCommandCenter];
    [commandCenter.playCommand setEnabled:false];
}
- (void) enablePlayCommand: (CDVInvokedUrlCommand *) command {
    MPRemoteCommandCenter *commandCenter = [MPRemoteCommandCenter sharedCommandCenter];
    [commandCenter.playCommand setEnabled:true];
}
- (void) disablePauseCommand: (CDVInvokedUrlCommand *) command {
    MPRemoteCommandCenter *commandCenter = [MPRemoteCommandCenter sharedCommandCenter];
    [commandCenter.pauseCommand setEnabled:false];
}
- (void) enablePauseCommand: (CDVInvokedUrlCommand *) command {
    MPRemoteCommandCenter *commandCenter = [MPRemoteCommandCenter sharedCommandCenter];
    [commandCenter.pauseCommand setEnabled:true];
}
- (void) disableNextTrackCommand: (CDVInvokedUrlCommand *) command {
    MPRemoteCommandCenter *commandCenter = [MPRemoteCommandCenter sharedCommandCenter];
    [commandCenter.nextTrackCommand setEnabled:false];
}
- (void) enableNextTrackCommand: (CDVInvokedUrlCommand *) command {
    MPRemoteCommandCenter *commandCenter = [MPRemoteCommandCenter sharedCommandCenter];
    [commandCenter.nextTrackCommand setEnabled:true];
}
- (void) disablePreviousTrackCommand: (CDVInvokedUrlCommand *) command {
    MPRemoteCommandCenter *commandCenter = [MPRemoteCommandCenter sharedCommandCenter];
    [commandCenter.previousTrackCommand setEnabled:false];
}
- (void) enablePreviousTrackCommand: (CDVInvokedUrlCommand *) command {
    MPRemoteCommandCenter *commandCenter = [MPRemoteCommandCenter sharedCommandCenter];
    [commandCenter.previousTrackCommand setEnabled:true];
}
- (void) disableTogglePlayPauseCommand: (CDVInvokedUrlCommand *) command {
    MPRemoteCommandCenter *commandCenter = [MPRemoteCommandCenter sharedCommandCenter];
    [commandCenter.togglePlayPauseCommand setEnabled:false];
}
- (void) enableTogglePlayPauseCommand: (CDVInvokedUrlCommand *) command {
    MPRemoteCommandCenter *commandCenter = [MPRemoteCommandCenter sharedCommandCenter];
    [commandCenter.togglePlayPauseCommand setEnabled:true];
}
- (void) disableChangePlaybackPositionCommand: (CDVInvokedUrlCommand *) command {
    MPRemoteCommandCenter *commandCenter = [MPRemoteCommandCenter sharedCommandCenter];
    [commandCenter.changePlaybackPositionCommand setEnabled:false];
}
- (void) enableChangePlaybackPositionCommand: (CDVInvokedUrlCommand *) command {
    MPRemoteCommandCenter *commandCenter = [MPRemoteCommandCenter sharedCommandCenter];
    [commandCenter.changePlaybackPositionCommand setEnabled:true];
}



- (MPMediaItemArtwork *) createCoverArtwork: (NSString *) coverUri {
    UIImage * coverImage = nil;
    
    if (coverUri == nil) {
        return nil;
    }
    
    if ([coverUri hasPrefix:@"http://"] || [coverUri hasPrefix:@"https://"]) {
        NSURL * coverImageUrl = [NSURL URLWithString:coverUri];
        NSData * coverImageData = [NSData dataWithContentsOfURL: coverImageUrl];
        
        coverImage = [UIImage imageWithData: coverImageData];
    }
    else if ([coverUri hasPrefix:@"file://"]) {
        NSString * fullCoverImagePath = [coverUri stringByReplacingOccurrencesOfString:@"file://" withString:@""];
        
        if ([[NSFileManager defaultManager] fileExistsAtPath: fullCoverImagePath]) {
            coverImage = [[UIImage alloc] initWithContentsOfFile: fullCoverImagePath];
        }
    }
    else if (![coverUri isEqual:@""]) {
        NSString * baseCoverImagePath = [NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES) objectAtIndex:0];
        NSString * fullCoverImagePath = [NSString stringWithFormat:@"%@%@", baseCoverImagePath, coverUri];
    
        if ([[NSFileManager defaultManager] fileExistsAtPath:fullCoverImagePath]) {
            coverImage = [UIImage imageNamed:fullCoverImagePath];
        }
    }
    else {
        coverImage = [UIImage imageNamed:@"none"];
    }
    
    return [self isCoverImageValid:coverImage] ? [[MPMediaItemArtwork alloc] initWithImage:coverImage] : nil;
}

- (bool) isCoverImageValid: (UIImage *) coverImage {
    return coverImage != nil && ([coverImage CIImage] != nil || [coverImage CGImage] != nil);
}

//Handle seeking with the progress slider on lockscreen or control center
- (MPRemoteCommandHandlerStatus)changedThumbSliderOnLockScreen:(MPChangePlaybackPositionCommandEvent *)event {
    NSString * seekTo = [NSString stringWithFormat:@"{\"message\":\"music-controls-seek-to\",\"position\":\"%f\"}", event.positionTime];
    CDVPluginResult * pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:seekTo];
    pluginResult.associatedObject = @{@"position":[NSNumber numberWithDouble: event.positionTime]};
    [self.commandDelegate sendPluginResult:pluginResult callbackId:[self latestEventCallbackId]];
    return MPRemoteCommandHandlerStatusSuccess;
}

//Handle the skip forward event
- (MPRemoteCommandHandlerStatus) skipForwardEvent:(MPSkipIntervalCommandEvent *)event {
    NSString * action = @"music-controls-skip-forward";
    NSString * jsonAction = [NSString stringWithFormat:@"{\"message\":\"%@\"}", action];
    CDVPluginResult * pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:jsonAction];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:[self latestEventCallbackId]];
    return MPRemoteCommandHandlerStatusSuccess;
}

//Handle the skip backward event
- (MPRemoteCommandHandlerStatus) skipBackwardEvent:(MPSkipIntervalCommandEvent *)event {
    NSString * action = @"music-controls-skip-backward";
    NSString * jsonAction = [NSString stringWithFormat:@"{\"message\":\"%@\"}", action];
    CDVPluginResult * pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:jsonAction];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:[self latestEventCallbackId]];
    return MPRemoteCommandHandlerStatusSuccess;
}

//If MPRemoteCommandCenter is enabled for any function we must enable it for all and register a handler
//So if we want to use the new scrubbing support in the lock screen we must implement dummy handlers
//for those functions that we already deal with through notifications (play, pause, skip etc)
//otherwise those remote control actions will be disabled
- (MPRemoteCommandHandlerStatus) remoteEvent:(MPRemoteCommandEvent *)event {
    return MPRemoteCommandHandlerStatusSuccess;
}

- (MPRemoteCommandHandlerStatus) nextTrackEvent:(MPRemoteCommandEvent *)event {
    NSString * action = @"music-controls-next";
    NSString * jsonAction = [NSString stringWithFormat:@"{\"message\":\"%@\"}", action];
    CDVPluginResult * pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:jsonAction];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:[self latestEventCallbackId]];
    return MPRemoteCommandHandlerStatusSuccess;
}

- (MPRemoteCommandHandlerStatus) prevTrackEvent:(MPRemoteCommandEvent *)event {
    NSString * action = @"music-controls-previous";
    NSString * jsonAction = [NSString stringWithFormat:@"{\"message\":\"%@\"}", action];
    CDVPluginResult * pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:jsonAction];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:[self latestEventCallbackId]];
    return MPRemoteCommandHandlerStatusSuccess;

}

- (MPRemoteCommandHandlerStatus) pauseEvent:(MPRemoteCommandEvent *)event {
    NSString * action = @"music-controls-pause";
    NSString * jsonAction = [NSString stringWithFormat:@"{\"message\":\"%@\"}", action];
    CDVPluginResult * pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:jsonAction];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:[self latestEventCallbackId]];
    return MPRemoteCommandHandlerStatusSuccess;

}

- (MPRemoteCommandHandlerStatus) playEvent:(MPRemoteCommandEvent *)event {
    NSString * action = @"music-controls-play";
    NSString * jsonAction = [NSString stringWithFormat:@"{\"message\":\"%@\"}", action];
    CDVPluginResult * pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:jsonAction];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:[self latestEventCallbackId]];
    return MPRemoteCommandHandlerStatusSuccess;

}

//Handle all other remote control events
- (void) handleMusicControlsNotification: (NSNotification *) notification {
    UIEvent * receivedEvent = notification.object;
    
    if ([self latestEventCallbackId] == nil) {
        return;
    }
    
    if (receivedEvent.type == UIEventTypeRemoteControl) {
        NSString * action;
        
        switch (receivedEvent.subtype) {
            case UIEventSubtypeRemoteControlTogglePlayPause:
                action = @"music-controls-toggle-play-pause";
                NSLog(@"%@", action);
                break;
                
            case UIEventSubtypeRemoteControlPlay:
                action = @"music-controls-play";
                NSLog(@"%@", action);
                break;
                
            case UIEventSubtypeRemoteControlPause:
                action = @"music-controls-pause";
                NSLog(@"%@", action);
                break;
                
            case UIEventSubtypeRemoteControlPreviousTrack:
                action = @"music-controls-previous";
                NSLog(@"%@", action);
                break;
                
            case UIEventSubtypeRemoteControlNextTrack:
                action = @"music-controls-next";
                NSLog(@"%@", action);
                break;
                
            case UIEventSubtypeRemoteControlStop:
                action = @"music-controls-destroy";
                NSLog(@"%@", action);
                break;
                
            default:
                action = nil;
                break;
        }
        
        if(action == nil){
            return;
        }
        
        NSString * jsonAction = [NSString stringWithFormat:@"{\"message\":\"%@\"}", action];
        CDVPluginResult * pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:jsonAction];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:[self latestEventCallbackId]];
    }
}

//There are only 3 button slots available so next/prev track and skip forward/back cannot both be enabled
//skip forward/back will take precedence if both are enabled
// musicControlsSettings is the object received from json
- (void) registerMusicControlsEventListener {
    if (_commandCenterRegistered) return;
    [[UIApplication sharedApplication] beginReceivingRemoteControlEvents];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(handleMusicControlsNotification:) name:@"musicControlsEventNotification" object:nil];
    
    //register required event handlers for standard controls
    MPRemoteCommandCenter *commandCenter = [MPRemoteCommandCenter sharedCommandCenter];
    [commandCenter.playCommand setEnabled:true];
    [commandCenter.playCommand addTarget:self action:@selector(playEvent:)];
    [commandCenter.pauseCommand setEnabled:true];
    [commandCenter.pauseCommand addTarget:self action:@selector(pauseEvent:)];
    [commandCenter.nextTrackCommand setEnabled:true];
    [commandCenter.nextTrackCommand addTarget:self action:@selector(nextTrackEvent:)];
    [commandCenter.previousTrackCommand setEnabled:true];
    [commandCenter.previousTrackCommand addTarget:self action:@selector(prevTrackEvent:)];
    //[commandCenter.togglePlayPauseCommand setEnabled:true];
    //[commandCenter.togglePlayPauseCommand addTarget:self action:@selector(togglePlayPauseTrackEvent:)];

    commandCenter.skipForwardCommand.preferredIntervals = @[@(musicControlsSettings.skipForwardInterval)];
    [commandCenter.skipForwardCommand setEnabled:false];
    [commandCenter.skipForwardCommand addTarget: self action:@selector(skipForwardEvent:)];
    commandCenter.skipBackwardCommand.preferredIntervals = @[@(musicControlsSettings.skipForwardInterval)];
    [commandCenter.skipBackwardCommand setEnabled:false];
    [commandCenter.skipBackwardCommand addTarget: self action:@selector(skipBackwardEvent:)];
    [commandCenter.changePlaybackPositionCommand setEnabled:true];
    [commandCenter.changePlaybackPositionCommand addTarget:self action:@selector(changedThumbSliderOnLockScreen:)];

    _commandCenterRegistered = YES;
}

- (void) deregisterMusicControlsEventListener {
    [[UIApplication sharedApplication] endReceivingRemoteControlEvents];
    [[NSNotificationCenter defaultCenter] removeObserver:self name:@"receivedEvent" object:nil];
    
    _commandCenterRegistered = NO;
    MPRemoteCommandCenter *commandCenter = [MPRemoteCommandCenter sharedCommandCenter];
    [commandCenter.nextTrackCommand removeTarget:self];
    [commandCenter.previousTrackCommand removeTarget:self];
    [commandCenter.playCommand removeTarget:self];
    [commandCenter.pauseCommand removeTarget:self];
    [commandCenter.togglePlayPauseCommand removeTarget:self];

    [commandCenter.changePlaybackPositionCommand setEnabled:false];
    [commandCenter.changePlaybackPositionCommand removeTarget:self action:NULL];
    [self setLatestEventCallbackId:nil];
}

- (void) dealloc {
    [self deregisterMusicControlsEventListener];
}

@end
