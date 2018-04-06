package com.zinc.jtinker;

import android.content.Context;
import android.util.Log;

import com.zinc.jtinker.utils.FileTimeComparator;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import dalvik.system.BaseDexClassLoader;
import dalvik.system.DexClassLoader;
import dalvik.system.PathClassLoader;

/**
 * @author Jiang zinc
 * @date 创建时间：2018/4/5
 * @description
 */

public class JThinkerManager {

    public static final String DEX_DIR = "odex";
    public static final String OPT_DEX = "opt_dex";

    //用于存放dex
    private static HashSet<File> loadedDex = new HashSet<>();
    private static String TAG = JThinkerManager.class.getSimpleName();

    static {
        loadedDex.clear();
    }

    public static void loadDex(Context context) {

        if (context == null) {
            return;
        }

        //会返回"data/data/包名/app_+DEX_DIR"
        File fileDir = context.getDir(DEX_DIR, Context.MODE_PRIVATE);
        File[] fileArray = fileDir.listFiles();
        List<File> fileList = Arrays.asList(fileArray);
        Collections.sort(fileList, new FileTimeComparator());

        for (File file : fileList) {
            //如果文件名字格式为：classesxx.xxx 或 xxx.dex 则进行存储
            if (file.getName().startsWith("classes") || file.getName().endsWith(".dex")) {
                Log.i(TAG, "loadDex: " + file.getAbsolutePath());
                loadedDex.add(file);
            }
        }

        String optimizeDir = fileDir.getAbsolutePath() + File.separator + OPT_DEX;
        File optFile = new File(optimizeDir);
        if (!optFile.exists()) {
            optFile.mkdirs();
        }

        PathClassLoader pathClassLoader = (PathClassLoader) context.getClassLoader();
        //系统的dexElement[]
        Object sysDexElement = getDexElement(pathClassLoader);

        //获取sysDexElement的元素类型
        Class<?> singleElementClazz = sysDexElement.getClass().getComponentType();
        //获取sysDexElement的长度（因为是object，需要借助Array的方法）
        int systemLength = Array.getLength(sysDexElement);

        List myDexElementList = new ArrayList<>();
        for (File dex : loadedDex) {
            /**
             * public DexClassLoader(String dexPath, String optimizedDirectory, String librarySearchPath, ClassLoader parent)
             * 参数:
             * 1、dexPath: dex的绝对路径
             * 2、optimizedDirectory: 目录优化DEX文件
             * 3、librarySearchPath: 依赖包缓存路径，可以传null，则以第一个参数为准
             * 4、parent: 类加载器，可以通过context获取
             */
            DexClassLoader dexClassLoader = new DexClassLoader(dex.getAbsolutePath(), optFile.getAbsolutePath(), null, context.getClassLoader());
            //补丁包的dexElements[]
            Object myDexElement = getDexElement(dexClassLoader);

            //当前dexElement长度
            int curEleCount = Array.getLength(myDexElement);

            for (int i = 0; i < curEleCount; ++i) {
                myDexElementList.add(Array.get(myDexElement, i));
            }

        }

        for (int i = 0; i < systemLength; ++i) {
            myDexElementList.add(Array.get(sysDexElement, i));
        }

        try {
            Object pathListObject = getPathListObject(pathClassLoader);

            Field elementsField = pathListObject.getClass().getDeclaredField("dexElements");
            elementsField.setAccessible(true);

            Object myDexElementArray = Array.newInstance(singleElementClazz, myDexElementList.size());
            for (int i = 0; i < myDexElementList.size(); ++i) {
                Array.set(myDexElementArray, i, myDexElementList.get(i));
            }

            elementsField.set(pathListObject, myDexElementArray);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * 目的：我们最终需要获取到DexPathList中的dexElements元素，dex是安卓虚拟机执行单元
     * 思路：
     * 1、通过上下文获取ClassLoader，而ClassLoader{@link java.lang.ClassLoader}是一个抽象类，
     * ====我们这里需要使用的是DexClassLoader，因为dex是安卓虚拟机执行单元
     * 2、但是DexClassLoader{@link dalvik.system.DexClassLoader}并没有什么属性和方法，往其基类
     * ====BaseDexClassLoader{@link dalvik.system.BaseDexClassLoader}搜索，可以看到有个属性pathList（是DexPathList类型）
     * 3、进入DexPathList{@link dalvik.system.DexPathList}，可以看到dexElements属性，这个便是我们需要获取到的系统帮我们加载进入的dex
     */
    /**
     * 获取dexElement元素
     *
     * @param baseDexClassLoader 可以是 PathClassLoader 或是 DexClassLoader
     */
    private static Object getDexElement(BaseDexClassLoader baseDexClassLoader) {
        try {
            Object pathListObject = getPathListObject(baseDexClassLoader);
            Object dexElementsObject = getDexElementsObject(pathListObject);
            return dexElementsObject;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 获取pathList
     *
     * @return
     */
    private static Object getPathListObject(BaseDexClassLoader baseDexClassLoader) throws Exception {
        Class baseDexClassLoaderClazz = Class.forName("dalvik.system.BaseDexClassLoader");
        Field pathListField = baseDexClassLoaderClazz.getDeclaredField("pathList");
        pathListField.setAccessible(true);
        Object pathListObject = pathListField.get(baseDexClassLoader);
        return pathListObject;
    }

    private static Object getDexElementsObject(Object pathListObject) throws Exception {
        //获取dexElements
        Class dexPathListClazz = pathListObject.getClass();
        Field dexElementsField = dexPathListClazz.getDeclaredField("dexElements");
        dexElementsField.setAccessible(true);
        Object dexElementsObject = dexElementsField.get(pathListObject);
        return dexElementsObject;
    }

}
