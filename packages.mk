# Update this when adding a new AndroidManifest.xml.
LOCAL_AAPT_FLAGS := \
	com.android.assets.product \
	com.android.assets.quantum \
	com.android.bubble \
	com.android.contacts.common \
	com.android.dialer.about \
	com.android.dialer.app \
	com.android.dialer.app.manifests.activities \
	com.android.dialer.assisteddialing \
	com.android.dialer.assisteddialing.ui \
	com.android.dialer.backup \
	com.android.dialer.binary.aosp.testing \
	com.android.dialer.binary.google \
	com.android.dialer.blocking \
	com.android.dialer.blockreportspam \
	com.android.dialer.callcomposer \
	com.android.dialer.callcomposer.camera \
	com.android.dialer.callcomposer.camera.camerafocus \
	com.android.dialer.callcomposer.cameraui \
	com.android.dialer.calldetails \
	com.android.dialer.calllog.config \
	com.android.dialer.calllog.database \
	com.android.dialer.calllog.ui \
	com.android.dialer.calllog.ui.menu \
	com.android.dialer.calllogutils \
	com.android.dialer.clipboard \
	com.android.dialer.commandline \
	com.android.dialer.common \
	com.android.dialer.common.concurrent.testing \
	com.android.dialer.common.preference \
	com.android.dialer.configprovider \
	com.android.dialer.contacts.displaypreference \
	com.android.dialer.contactphoto \
	com.android.dialer.contactsfragment \
	com.android.dialer.databasepopulator \
	com.android.dialer.dialpadview \
	com.android.dialer.dialpadview.theme \
	com.android.dialer.enrichedcall.simulator \
	com.android.dialer.feedback \
	com.android.dialer.glidephotomanager.impl \
  com.android.dialer.historyitemactions \
	com.android.dialer.interactions \
	com.android.dialer.lettertile \
	com.android.dialer.location \
	com.android.dialer.main.impl \
	com.android.dialer.main.impl.toolbar \
	com.android.dialer.main.impl.bottomnav \
	com.android.dialer.notification \
	com.android.dialer.oem \
	com.android.dialer.phonelookup.database \
	com.android.dialer.phonenumberutil \
	com.android.dialer.postcall \
	com.android.dialer.precall.impl \
	com.android.dialer.precall.externalreceiver \
	com.android.dialer.preferredsim.impl \
	com.android.dialer.preferredsim.suggestion \
	com.android.dialer.promotion.impl \
	com.android.dialer.rtt \
	com.android.dialer.searchfragment.common \
	com.android.dialer.searchfragment.cp2 \
	com.android.dialer.searchfragment.directories \
	com.android.dialer.searchfragment.list \
	com.android.dialer.searchfragment.nearbyplaces \
	com.android.dialer.searchfragment.remote \
	com.android.dialer.shortcuts \
	com.android.dialer.simulator.impl \
	com.android.dialer.simulator.service \
	com.android.dialer.spam.promo \
	com.android.dialer.speeddial \
	com.android.dialer.spannable \
	com.android.dialer.theme \
	com.android.dialer.theme.base \
	com.android.dialer.theme.base.impl \
	com.android.dialer.theme.common \
	com.android.dialer.theme.hidden \
	com.android.dialer.util \
	com.android.dialer.voicemail.listui \
	com.android.dialer.voicemail.listui.error \
	com.android.dialer.voicemail.listui.menu \
	com.android.dialer.voicemail.settings \
	com.android.dialer.voicemailstatus \
	com.android.dialer.widget \
	com.android.incallui \
	com.android.incallui.answer.impl.affordance \
	com.android.incallui.answer.impl \
	com.android.incallui.answer.impl.answermethod \
	com.android.incallui.answer.impl.hint \
	com.android.incallui.audioroute \
	com.android.incallui.autoresizetext \
	com.android.incallui.calllocation.impl \
	com.android.incallui.callpending \
	com.android.incallui.commontheme \
	com.android.incallui.contactgrid \
	com.android.incallui.disconnectdialog \
	com.android.incallui.hold \
	com.android.incallui.incall.impl \
	com.android.incallui.rtt.impl \
	com.android.incallui.rtt.protocol \
  com.android.incallui.speakeasy \
	com.android.incallui.sessiondata \
	com.android.incallui.spam \
	com.android.incallui.speakerbuttonlogic \
	com.android.incallui.telecomeventui \
	com.android.incallui.video.impl \
	com.android.incallui.video.protocol \
	com.android.phoneapphelper \
	com.android.voicemail \
	com.android.voicemail.impl \
	com.android.voicemail.impl.configui \
