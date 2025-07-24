package com.sean.rao.ali_auth.login;

import static com.sean.rao.ali_auth.utils.AppUtils.dp2px;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.content.Context;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.IntRange;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.mobile.auth.gatewayauth.AuthUIConfig;
import com.mobile.auth.gatewayauth.CustomInterface;
import com.mobile.auth.gatewayauth.PhoneNumberAuthHelper;
import com.mobile.auth.gatewayauth.PreLoginResultListener;
import com.mobile.auth.gatewayauth.ResultCode;
import com.mobile.auth.gatewayauth.TokenResultListener;
import com.mobile.auth.gatewayauth.AuthRegisterViewConfig;
import com.mobile.auth.gatewayauth.model.TokenRet;
import com.sean.rao.ali_auth.R;
import com.sean.rao.ali_auth.common.LoginParams;
import com.sean.rao.ali_auth.config.BaseUIConfig;
import com.sean.rao.ali_auth.utils.UtilTool;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.flutter.plugin.common.EventChannel;



/**
 * 进app直接登录的场景
 */
public class OneKeyLoginPublic extends LoginParams {
    private static final String TAG = OneKeyLoginPublic.class.getSimpleName();
    private static final int alertWidth = 300;
    private static final int alertHeight = 195;
    private static final int buttonWidth = alertWidth / 2;
    private static final int buttonHeight = 48;
    private static final int buttonOffsetY = 15;

    public OneKeyLoginPublic(Activity activity, EventChannel.EventSink _eventSink, Object arguments){
        mActivity = activity;
        mContext = activity.getBaseContext();
        eventSink = _eventSink;
        jsonObject = formatParmas(arguments);
        config = getFormatConfig(jsonObject);

        // 初始化SDK
        sdkInit();
        customPrivacyAlert();
        mUIConfig = BaseUIConfig.init(jsonObject.getIntValue("pageType"));
        if (jsonObject.getBooleanValue("isDelay")) {
        } else {
            // 非延时的情况下需要判断是否给予登录
            mAuthHelper.quitLoginPage();
            oneKeyLogin();
        }
    }

    /**
     * 初始化SDK
     */
    private void sdkInit() {
        mTokenResultListener=new TokenResultListener() {
            @Override
            public void onTokenSuccess(String s) {
                sdkAvailable = true;
                try {
                    Log.i(TAG, "checkEnvAvailable：" + s);
                    TokenRet tokenRet = TokenRet.fromJson(s);
                    if (ResultCode.CODE_ERROR_ENV_CHECK_SUCCESS.equals(tokenRet.getCode())) {
                        /// 延时登录的情况下加速拉起一键登录页面
                        if (jsonObject.getBooleanValue("isDelay")) {
                            accelerateLoginPage(5000);
                        }
                    }

                    if (ResultCode.CODE_SUCCESS.equals(tokenRet.getCode())) {
                        Log.i("TAG", "获取token成功：" + s);
                        mAuthHelper.setAuthListener(null);
                        if (jsonObject.getBooleanValue("autoQuitPage")) {
                            mAuthHelper.quitLoginPage();
                        }
                    }
                    showResult(tokenRet.getCode(), null, tokenRet.getToken());
                } catch (Exception e) {
                    e.fillInStackTrace();
                }
            }

            @Override
            public void onTokenFailed(String s) {
                sdkAvailable = false;
                mAuthHelper.hideLoginLoading();
                Log.e(TAG, "获取token失败：" + s);
                try {
                    TokenRet tokenRet = TokenRet.fromJson(s);
                    List<String> skip = Collections.singletonList(ResultCode.CODE_ERROR_USER_SWITCH);
                    if (!skip.contains(tokenRet.getCode())) {
                        showResult(tokenRet.getCode(), tokenRet.getMsg(),null);
                    }
                } catch (Exception e) {
                    e.fillInStackTrace();
                }
                mAuthHelper.setAuthListener(null);
            }
        };
        mAuthHelper=PhoneNumberAuthHelper.getInstance(mContext, mTokenResultListener);
        mAuthHelper.getReporter().setLoggerEnable(jsonObject.getBooleanValue("isDebug"));
        mAuthHelper.setAuthSDKInfo(jsonObject.getString("androidSk"));

        /// 延时的情况下进行预取号，加快拉取授权页面
        if (jsonObject.getBooleanValue("isDelay")) {
            mAuthHelper.checkEnvAvailable(PhoneNumberAuthHelper.SERVICE_TYPE_LOGIN);
        }
    }

    /**
     * 延时登录操作
     * @param timeout
     */
    public void startLogin(int timeout){
        if (sdkAvailable) {
            mAuthHelper.quitLoginPage();
            getLoginToken(timeout);
        } else {
            //如果环境检查失败 使用其他登录方式
        }
    }

    /**
     * 返回默认上网卡运营商
     * @param
     * @return CMCC(移动)、CUCC(联通)、CTCC(电信)
     */
    public String getCurrentCarrierName(){
        return mAuthHelper.getCurrentCarrierName();
    }

    /**
     * 进入app就需要登录的场景使用
     */
    private void oneKeyLogin() {
        mAuthHelper = PhoneNumberAuthHelper.getInstance(mActivity.getApplicationContext(), mTokenResultListener);
        mAuthHelper.checkEnvAvailable(2);
        mUIConfig.configAuthPage();
        mAuthHelper.getLoginToken(mContext, 5000);
    }

    /**
     * 在不是一进app就需要登录的场景 建议调用此接口 加速拉起一键登录页面
     * 等到用户点击登录的时候 授权页可以秒拉
     * 预取号的成功与否不影响一键登录功能，所以不需要等待预取号的返回。
     * @param timeout
     */
    private void accelerateLoginPage(int timeout) {
        mAuthHelper.accelerateLoginPage(timeout, new PreLoginResultListener() {
            @Override
            public void onTokenSuccess(String s) {
                Log.e(TAG, "预取号成功: " + s);
                showResult("600016", null, s);
            }
            @Override
            public void onTokenFailed(String s, String s1) {
                Log.e(TAG, "预取号失败：" + ", " + s1);
                JSONObject jsonDataObj = new JSONObject();
                jsonDataObj.put("name", s);
                jsonDataObj.put("name1", s1);
                showResult("600012", null, jsonDataObj);
            }
        });
    }

    /**
     * 拉起授权页
     * @param timeout 超时时间
     */
    public void getLoginToken(int timeout) {
        mUIConfig.configAuthPage();
        mTokenResultListener = new TokenResultListener() {
            @Override
            public void onTokenSuccess(String s) {
                TokenRet tokenRet = TokenRet.fromJson(s);
                try {
                    if (ResultCode.CODE_START_AUTHPAGE_SUCCESS.equals(tokenRet.getCode())) {
                        Log.i(TAG, "唤起授权页成功：" + s);
                    }
                    showResult(tokenRet.getCode(), tokenRet.getMsg(),tokenRet.getToken());
                    if (ResultCode.CODE_SUCCESS.equals(tokenRet.getCode())) {
                        Log.i(TAG, "获取token成功：" + s);
                        mAuthHelper.setAuthListener(null);
                        if (jsonObject.getBooleanValue("autoQuitPage")) {
                            mAuthHelper.quitLoginPage();
                        }
                    }
                } catch (Exception e) {
                    e.fillInStackTrace();
                }
            }

            @Override
            public void onTokenFailed(String s) {
                Log.e(TAG, "获取token失败：" + s);
                //如果环境检查失败 使用其他登录方式
                try {
                    TokenRet tokenRet = TokenRet.fromJson(s);
                    showResult(tokenRet.getCode(), tokenRet.getMsg(),null);
                } catch (Exception e) {
                    e.fillInStackTrace();
                }
                // 失败时也不关闭
                mAuthHelper.setAuthListener(null);
                if (jsonObject.getBooleanValue("autoQuitPage")) {
                    mAuthHelper.quitLoginPage();
                }
            }
        };
        mAuthHelper.setAuthListener(mTokenResultListener);
        mAuthHelper.getLoginToken(mContext, timeout);
    }


    /**
     * SDK环境检查函数，检查终端是否⽀持号码认证，通过TokenResultListener返回code
     * type 1：本机号码校验 2: ⼀键登录
     * 600024 终端⽀持认证
     * 600013 系统维护，功能不可⽤
     */
    public void checkEnvAvailable(@IntRange(from = 1, to = 2) int type){
        mAuthHelper.checkEnvAvailable(PhoneNumberAuthHelper.SERVICE_TYPE_LOGIN);
    }

    /**
     * 退出授权页面
     */
    public void quitPage(){
        mAuthHelper.quitLoginPage();
    }

    /**
     * 结束授权页loading dialog
     */
    public void hideLoading(){
        mAuthHelper.hideLoginLoading();
    }

    /**
     * 处理参数，对参数进行处理包含color、Path
     * @param parmas
     * @return
     */
    private JSONObject formatParmas(Object parmas){
        JSONObject formatData = JSONObject.parseObject(JSONObject.toJSONString(parmas));
        for (Map.Entry<String, Object> entry : formatData.entrySet()) {
            // System.out.println(entry.getKey() + "----" + entry.getValue());
            // 判断是否使眼色代码
            if (entry.getKey().toLowerCase().contains("color") && String.valueOf(entry.getValue()).contains("#")) {
                System.out.println(entry.getKey() + "----" + entry.getValue());
                formatData.put(entry.getKey(), Color.parseColor(formatData.getString(entry.getKey())));
            }
            // 判断是否时路径字段
            // 排除按钮状态的背景logBtnBackgroundPath
            else if (
                    !entry.getKey().contains("logBtnBackgroundPath") &&
                    entry.getKey().toLowerCase().contains("path") &&
                    !formatData.getString(entry.getKey()).isEmpty() &&
                    !formatData.getString(entry.getKey()).contains("http")
            ) {
                formatData.put(entry.getKey(), UtilTool.flutterToPath(formatData.getString(entry.getKey())));
            } else {
                System.out.println(entry.getKey() + "--------------" + entry.getValue());
                formatData.put(entry.getKey(), entry.getValue());
            }
        }

        return formatData;
    }

    protected View initCancelView() {
        // 创建最外层垂直方向的LinearLayout
        LinearLayout containerLayout = new LinearLayout(mContext);
        containerLayout.setOrientation(LinearLayout.VERTICAL);
        int marginPx = dp2px(mActivity, buttonOffsetY);
        containerLayout.setPadding(0, marginPx, 0, 0);

        containerLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        // 创建顶部分割线
        View topDivider = new View(mContext);
        LinearLayout.LayoutParams topDividerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1); // 1px高度
        topDivider.setBackgroundColor(Color.parseColor("#DDDDDD"));
        topDivider.setLayoutParams(topDividerParams);
        containerLayout.addView(topDivider);

        // 创建水平方向的LinearLayout，用于放置按钮和竖线
        LinearLayout buttonContainer = new LinearLayout(mContext);
        buttonContainer.setOrientation(LinearLayout.HORIZONTAL);
        buttonContainer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        // 创建取消按钮
        TextView cancelButton = new TextView(mContext);
        // 使用 LayoutParams 来确定大小
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                dp2px(mActivity, buttonWidth),  // 宽度设置为父容器的一半
                dp2px(mActivity, buttonHeight)
        );
        // 使用 margins来确定位置
        buttonParams.setMargins(0, dp2px(mActivity, -1), 0, 0);
        cancelButton.setLayoutParams(buttonParams);
        cancelButton.setText("不同意");
        cancelButton.setTextColor(Color.parseColor("#FF333333"));
        cancelButton.setGravity(Gravity.CENTER);
        cancelButton.setBackgroundColor(Color.TRANSPARENT);
        cancelButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16.0F);

        // 创建竖线分割线
        View verticalDivider = new View(mContext);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                1, // 1px宽度
                dp2px(mActivity, buttonHeight)
        );
        dividerParams.setMargins(0, 0, 0, 0);
        verticalDivider.setLayoutParams(dividerParams);
        verticalDivider.setBackgroundColor(Color.parseColor("#DDDDDD"));

        // 添加取消按钮和竖线到水平容器中
        buttonContainer.addView(cancelButton);
        buttonContainer.addView(verticalDivider);

        // 将水平容器添加到最外层容器中
        containerLayout.addView(buttonContainer);

        return containerLayout;
    }

    private void customPrivacyAlert() {
        mAuthHelper.removePrivacyAuthRegisterViewConfig();
        mAuthHelper.addPrivacyAuthRegistViewConfig("cancel_dialog", new AuthRegisterViewConfig.Builder()
                .setView(initCancelView())
                .setRootViewId(AuthRegisterViewConfig.RootViewId.ROOT_VIEW_ID_NUMBER)
                .setCustomInterface(new CustomInterface() {
                    @Override
                    public void onClick(Context context) {
                        mAuthHelper.quitPrivacyPage();
                    }
                }).build());
    }

    /**
     * 对配置参数进行格式化并且转换
     * @param jsonObject
     * @return
     */
    private AuthUIConfig.Builder getFormatConfig(JSONObject jsonObject){
        AuthUIConfig.Builder config = JSON.parseObject(JSONObject.toJSONString(jsonObject), AuthUIConfig.Builder.class)
                .setPrivacyAlertBtnOffsetX(buttonWidth)
                .setPrivacyAlertBtnOffsetY(buttonOffsetY)
                .setBottomNavColor(Color.TRANSPARENT) // 底部导航栏颜色，可以让授权页和二次弹窗都全屏
                .setStatusBarColor(Color.WHITE)
                .setLightColor(true)
                .setStatusBarUIFlag(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
                .setTapPrivacyAlertMaskCloseAlert(false)
                .setPrivacyAlertMaskIsNeedShow(true)
                .setVendorPrivacyPrefix("《")
                .setVendorPrivacySuffix("》")
                .setPrivacyAlertEntryAnimation("in_activity")   // 弹窗进入动画，不设置在某些机型动画不是预期的渐显
                .setPrivacyAlertExitAnimation("out_activity")   // 弹窗进入动画，不设置在某些机型动画不是预期的渐隐


                .setWebNavColor(Color.WHITE)
                .setWebViewStatusBarColor(Color.WHITE)
                .setWebNavTextColor(Color.BLACK)
                .setWebNavTextSizeDp(18)
                ;

        config.setLogBtnToastHidden(true);
        config.setPrivacyAlertWidth(alertWidth);
        config.setPrivacyAlertHeight(alertHeight);
        config.setPrivacyAlertCornerRadiusArray(new int[]{12,12,12,12});

        config.setPrivacyAlertTitleTextSize(16);
        config.setPrivacyAlertTitleOffsetY(20);
        config.setPrivacyAlertTitleColor(Color.parseColor("#FF333333"));

        config.setPrivacyAlertContentTextSize(14);
        config.setPrivacyAlertContentColor(Color.parseColor("#FF0C53FF"));
        config.setPrivacyAlertContentBaseColor(Color.parseColor("#FF333333"));
        config.setPrivacyAlertContentHorizontalMargin(20);
        config.setPrivacyAlertContentAlignment(Gravity.CENTER);
        config.setPrivacyAlertContentVerticalMargin(10);

        config.setPrivacyAlertBtnWidth(buttonWidth);
        config.setPrivacyAlertBtnHeigth(buttonHeight);

        config.setPrivacyAlertBtnBackgroundImgDrawable(new ColorDrawable(Color.TRANSPARENT)); // WHITE
        config.setPrivacyAlertBtnTextColor(Color.parseColor("#FF0C53FF"));
        config.setPrivacyAlertBtnTextSize(16);
        config.setPrivacyAlertBtnContent("同意并登录");

        config.setPrivacyBefore("阅读并同意");
        config.setPrivacyEnd("，我们将严格保护你的隐私。");
        config.setPrivacyAlertCloseBtnShow(false); // 关闭按钮不展示
        config.setTapAuthPageMaskClosePage(false); // 点击遮罩不关闭页面


        // 设置按钮的背景
        // 20230518 修正错误 setLoadingBackgroundPath -> setLogBtnBackgroundPath
        if (jsonObject.getString("logBtnBackgroundPath") != null && jsonObject.getString("logBtnBackgroundPath").contains(",")) {
            config.setLogBtnBackgroundDrawable(UtilTool.getStateListDrawable(mContext, jsonObject.getString("logBtnBackgroundPath")));
        } else {
            config.setLogBtnBackgroundPath(UtilTool.flutterToPath(jsonObject.getString("logBtnBackgroundPath")));
        }
        /**
         *  authPageActIn = var1;
         *  activityOut = var2;
         */
        if(UtilTool.dataStatus(jsonObject, "authPageActIn") && UtilTool.dataStatus(jsonObject, "activityOut")){
            config.setAuthPageActIn(jsonObject.getString("authPageActIn"), jsonObject.getString("activityOut"));
        }
        /**
         *  authPageActOut = var1;
         *  activityIn = var2;
         */
        if(UtilTool.dataStatus(jsonObject, "authPageActOut") && UtilTool.dataStatus(jsonObject, "activityIn")){
            config.setAuthPageActIn(jsonObject.getString("authPageActOut"), jsonObject.getString("activityIn"));
        }
        /**
         *  protocolOneName = var1;
         *  protocolOneURL = var2;
         */
        if(UtilTool.dataStatus(jsonObject, "protocolOneName") && UtilTool.dataStatus(jsonObject, "protocolOneURL")){
            config.setAppPrivacyOne(jsonObject.getString("protocolOneName"), jsonObject.getString("protocolOneURL"));
        }
        /**
         *  protocolTwoName = var1;
         *  protocolTwoURL = var2;
         */
        if(UtilTool.dataStatus(jsonObject, "protocolTwoName") && UtilTool.dataStatus(jsonObject, "protocolTwoURL")){
            config.setAppPrivacyTwo(jsonObject.getString("protocolTwoName"), jsonObject.getString("protocolTwoURL"));
        }
        /**
         *  protocolThreeName = var1;
         *  protocolThreeURL = var2;
         */
        if(UtilTool.dataStatus(jsonObject, "protocolThreeName") && UtilTool.dataStatus(jsonObject, "protocolThreeURL")){
            config.setAppPrivacyThree(jsonObject.getString("protocolThreeName"), jsonObject.getString("protocolThreeURL"));
        }
        /**
         *  protocolColor = var1;
         *  protocolOneColor = var2;
         *  protocolTwoColor = var2;
         *  protocolThreeColor = var2;
         */
        if(UtilTool.dataStatus(jsonObject, "protocolColor") && UtilTool.dataStatus(jsonObject, "protocolCustomColor")){
            config.setAppPrivacyColor(jsonObject.getIntValue("protocolColor"), jsonObject.getIntValue("protocolCustomColor"));
        }
        return config;
    }
}

