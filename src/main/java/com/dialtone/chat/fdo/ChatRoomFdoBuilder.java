/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.chat.fdo;

import com.atomforge.fdo.dsl.FdoScript;
import com.atomforge.fdo.dsl.StreamBuilder;
import com.atomforge.fdo.dsl.atoms.*;
import com.atomforge.fdo.dsl.values.Criterion;
import com.atomforge.fdo.model.FdoGid;
import com.dialtone.chat.ChatRoom;
import com.dialtone.chat.User;

import java.util.Map;

public final class ChatRoomFdoBuilder {

    private static final FdoGid CHAT_WINDOW_GID = FdoGid.of(19, 0, 0);
    private static final FdoGid CHAT_FORM_GID = FdoGid.of(32, 256);

    private final ChatRoom chatRoom;
    private final String screenname;
    private final Map<String, Integer> userTagMap;

    public ChatRoomFdoBuilder(ChatRoom chatRoom, String screenname, Map<String, Integer> userTagMap) {
        this.chatRoom = chatRoom;
        this.screenname = screenname != null ? screenname : "Guest";
        this.userTagMap = userTagMap;
    }

    public String toSource() {
        String windowTitle = chatRoom.getTitle() + " - [" + screenname + "]";

        StreamBuilder stream = FdoScript.stream()
                .uniStartStream()
                .manPresetGid(CHAT_WINDOW_GID)
                .ifLastReturnFalseThen(1, 0) // Note: original had only 1 arg, but method requires 2 (trueBranch, falseBranch)
                .uniStartStream("01x")
                .uniInvokeNoContext(CHAT_FORM_GID)
                .manSetContextGlobalId(CHAT_WINDOW_GID)
                .actSetCriterion(Criterion.CLOSE)
                .actReplaceAction(nested -> {
                    nested.uniStartStream()
                            .manClose(CHAT_WINDOW_GID)
                            .smSendTokenRaw("CL")
                            .uniEndStream();
                })
                .manReplaceData(windowTitle)
                .varNumberSet("A", 1)
                .varNumberSave("A", 65537)
                .uniEndStream("01x")
                .atom(ChatAtom.ROOM_OPEN); // CHAT protocol methods not yet implemented

        addUsers(stream);

        return stream
                .uniStartStream()
                    .manSetContextRelative(257)
                    .manGetChildCount()
                    .uniConvertLastAtomData()
                    .uniSaveResult()
                    .manChangeContextRelative(261)
                    .uniGetResult()
                    .uniUseLastAtomString(ManAtom.REPLACE_DATA)
                    .manEndContext()
                    .manUpdateDisplay()
                    .uniEndStream()
                    .uniStartStream()
                    .smSendTokenRaw("CO")
                    .uniEndStream()
                    .uniSyncSkip(1)
                    .manUpdateDisplay()
                    .manMakeFocus()
                    .uniWaitOff()
                .uniEndStream()
                .toSource();
    }

    private void addUsers(StreamBuilder stream) {
        for (User user : chatRoom.getUsers()) {
            String username = user.getUsername();
            Integer userTag = userTagMap.get(username);
            stream.atom(ChatAtom.ADD_USER, username) // CHAT protocol methods not yet implemented
                    .matRelativeTag(userTag)
                    .actSetInheritance(0x02) // Note: original had "02x" string, converted to int
                    .atom(ChatAtom.END_OBJECT); // CHAT protocol methods not yet implemented
        }
    }
}