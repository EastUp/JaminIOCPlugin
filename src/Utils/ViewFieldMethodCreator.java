package Utils;

import View.FindViewByIdDialog;
import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.openapi.command.WriteCommandAction.Simple;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import entity.Element;

import java.util.ArrayList;
import java.util.List;

public class ViewFieldMethodCreator extends Simple {

    private FindViewByIdDialog mDialog;
    private Editor mEditor;
    private PsiFile mFile;
    private Project mProject;
    private PsiClass mClass;
    private List<Element> mElements;
    private PsiElementFactory mFactory;

    private boolean mMethodGenerateError = false; //方法生成是否错误，如果已经存在了同名的方法这个值就为tru

    public ViewFieldMethodCreator(FindViewByIdDialog dialog, Editor editor, PsiFile psiFile, PsiClass psiClass, String command, List<Element> elements, String selectedText) {
        super(psiClass.getProject(), command);
        mDialog = dialog;
        mEditor = editor;
        mFile = psiFile;
        mProject = psiClass.getProject();
        mClass = psiClass;
        mElements = elements;
        // 获取Factory
        mFactory = JavaPsiFacade.getElementFactory(mProject);
    }

    @Override
    protected void run() throws Throwable {
        mMethodGenerateError = false;
        try {
            generateFields();
            generateOnClickMethod();
        } catch (Exception e) {
            // 异常打印
            mDialog.cancelDialog();
            Util.showPopupBalloon(mEditor, e.getMessage(), 10);
            return;
        }
        // 重写class
        JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(mProject);
        styleManager.optimizeImports(mFile);
        styleManager.shortenClassReferences(mClass);
        new ReformatCodeProcessor(mProject, mClass.getContainingFile(), null, false).runWithoutProgress();
        if(!mMethodGenerateError)
            Util.showPopupBalloon(mEditor, "生成成功", 5);
    }

    /**
     * 创建变量
     */
    private void generateFields() {
        for (Element element : mElements) {
            if (mClass.getText().contains("@BindView(" + element.getFullID() + ")")) {
                // 不创建新的变量
                continue;
            }
//            // 设置变量名，获取text里面的内容
//            String text = element.getXml().getAttributeValue("android:text");
//            if (TextUtils.isEmpty(text)) {
//                // 如果是text为空，则获取hint里面的内容
//                text = element.getXml().getAttributeValue("android:hint");
//            }
//            // 如果是@string/app_name类似
//            if (!TextUtils.isEmpty(text) && text.contains("@string/")) {
//                text = text.replace("@string/", "");
//                // 获取strings.xml
//                PsiFile[] psiFiles = FilenameIndex.getFilesByName(mProject, "strings.xml", GlobalSearchScope.allScope(mProject));
//                if (psiFiles.length > 0) {
//                    for (PsiFile psiFile : psiFiles) {
//                        // 获取src\main\res\values下面的strings.xml文件
//                        String dirName = psiFile.getParent().toString();
//                        if (dirName.contains("src\\main\\res\\values")) {
//                            text = Util.getTextFromStringsXml(psiFile, text);
//                        }
//                    }
//                }
//            }

            StringBuilder fromText = new StringBuilder();
//            if (!TextUtils.isEmpty(text)) {
//                fromText.append("/****" + text + "****/\n");
//            }
            fromText.append("@BindView(" + element.getFullID() + ")\n");
            fromText.append("private ");
            fromText.append(element.getName());
            fromText.append(" ");
            fromText.append(element.getFieldName());
            fromText.append(";");
            // 创建点击方法
            if (element.isCreateFiled()) {
                // 添加到class
                mClass.add(mFactory.createFieldFromText(fromText.toString(), mClass));
            }
        }
    }

    /**
     * 创建OnClick方法
     */
    private void generateOnClickMethod() {

        boolean noCheckNet = judgeNoCheckNet();
        boolean allCheckNet = judgeAllCheckNet();

        //可以只创建一个方法
        if (noCheckNet || allCheckNet) {
            for (Element element : mElements) {
                // 可以使用并且可以点击
                if (element.isCreateClickMethod()) {
                    // 需要创建OnClick方法
                    String methodName = "onClick";
                    PsiMethod[] onClickMethods = mClass.findMethodsByName(methodName, true);
                    boolean clickMethodExist = onClickMethods.length > 0;
                    if (!clickMethodExist) // 创建点击方法
                        createClickMethod(methodName, element, true);
                    else{
                        mMethodGenerateError = true;
                        Util.showPopupBalloon(mEditor,"已经存在该方法了，如果需要重新生成请先删除",4);
                    }
                    return;
                }
            }
        } else {
            for (Element element : mElements) {
                // 可以使用并且可以点击
                if (element.isCreateClickMethod()) {
                    // 需要创建OnClick方法
                    String methodName = getClickMethodName(element) + "Click";
                    PsiMethod[] onClickMethods = mClass.findMethodsByName(methodName, true);
                    boolean clickMethodExist = onClickMethods.length > 0;
                    if (!clickMethodExist) {
                        // 创建点击方法
                        createClickMethod(methodName, element, false);
                    }
                }
            }
        }
    }

    /**
     * 是否全部选中CheckNet
     */
    private boolean judgeAllCheckNet() {
        for (Element element : mElements) {
            if (!element.isCreateCheckNetAnnotation()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 是否全部没有选中CheckNet
     */
    private boolean judgeNoCheckNet() {
        for (Element element : mElements) {
            if (element.isCreateCheckNetAnnotation()) {
                return false;
            }
        }
        return true;
    }


    /**
     * 创建一个点击事件
     *
     * @param oneClickMethod 只创建一个点击事件方法
     */
    private void createClickMethod(String methodName, Element element, boolean oneClickMethod) {
        // 拼接方法的字符串
        StringBuilder methodBuilder = new StringBuilder();
        if (element.isCreateCheckNetAnnotation())
            methodBuilder.append("@CheckNet\n");
        if (!oneClickMethod)
            methodBuilder.append("@BindClick(" + element.getFullID() + ")\n");
        else {
            List<String> idList = new ArrayList<>();
            for (Element element1 : mElements) {
                if (element1.isCreateClickMethod())
                    idList.add(element1.getFullID());
            }
            if(idList.size() <= 1){
                methodBuilder.append("@BindClick(" + TextUtils.join(",",idList.toArray()) + ")\n");
            }else{
                methodBuilder.append("@BindClick({" + TextUtils.join(",",idList.toArray()) + "})\n");
            }

        }
//        methodBuilder.append("private void " + methodName + "(" + element.getName() + " " + getClickMethodName(element) + "){");
        methodBuilder.append("private void " + methodName + "(View view){");
        methodBuilder.append("\n}");
        // 创建OnClick方法
        mClass.add(mFactory.createMethodFromText(methodBuilder.toString(), mClass));
    }

    /**
     * 获取点击方法的名称
     */
    public String getClickMethodName(Element element) {
        String[] names = element.getId().split("_");
        // aaBbCc
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < names.length; i++) {
            if (i == 0) {
                sb.append(names[i]);
            } else {
                sb.append(Util.firstToUpperCase(names[i]));
            }
        }
        return sb.toString();
    }
}
