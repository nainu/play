package play.data.validation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.sf.oval.configuration.annotation.AbstractAnnotationCheck;
import play.classloading.enhancers.LocalvariablesNamesEnhancer.LocalVariablesNamesTracer;
import play.exceptions.UnexpectedException;

public class Validation {

    static ThreadLocal<Validation> current = new ThreadLocal<Validation>();
    List<Error> errors = new ArrayList();
    boolean keep = false;
    
    protected Validation() {
    }

    static Validation current() {
        return current.get();
    }

    public static List<Error> errors() {
        return current.get().errors;
    }

    public Map<String, List<Error>> errorsMap() {
        Map<String, List<Error>> result = new HashMap<String, List<Error>>();
        for (Error error : errors()) {
            result.put(error.key, errors(error.key));
        }
        return result;
    }

    public static void addError(String key, String message, String... variables) {
        Validation.current().errors.add(new Error(key, message, variables));
    }

    public static boolean hasErrors() {
        return current.get().errors.size() > 0;
    }

    public static Error error(String key) {
        for (Error error : current.get().errors) {
            if (error.key.equals(key)) {
                return error;
            }
        }
        return null;
    }

    public static List<Error> errors(String key) {
        List<Error> errors = new ArrayList<Error>();
        for (Error error : current.get().errors) {
            if (error.key.equals(key)) {
                errors.add(error);
            }
        }
        return errors;
    }

    public static void keep() {
        current.get().keep = true;
    }

    public static boolean hasError(String key) {
        return error(key) != null;
    }
    
    // ~~~~ Integration helper
    
    public static Map<String,List<Validator>> getValidators(Class clazz, String name) {
        Map<String,List<Validator>> result = new HashMap<String,List<Validator>>();
        searchValidator(clazz, name, result);
        return result;
    }
    
    static void searchValidator(Class clazz, String name, Map<String,List<Validator>> result) {
        for(Field field : clazz.getDeclaredFields()) {
            
            List<Validator> validators = new ArrayList<Validator>();
            String key = name + "." + field.getName();
            boolean containsAtValid = false;
            for(Annotation annotation : field.getDeclaredAnnotations()) {
                if(annotation.annotationType().getName().startsWith("play.data.validation")) {
                    Validator validator = new Validator(annotation);
                    validators.add(validator);
                    if(annotation.annotationType().equals(Equals.class)) {
                        validator.params.put("equalsTo", name + "." + ((Equals)annotation).value());
                    }
                }
                if(annotation.annotationType().equals(Valid.class)) {
                    containsAtValid = true;
                }
            }
            if(!validators.isEmpty()) {
                result.put(key, validators);
            }
            if(containsAtValid) {
                searchValidator(field.getType(), key, result);
            }
        }
    }
    
    public static class Validator {
        
        public Annotation annotation;
        public Map<String,Object> params = new HashMap();
        
        public Validator(Annotation annotation) {
            this.annotation = annotation;
        }
        
    }
    
    // ~~~~ Validations
    
    public static class ValidationResult {
        
        public boolean ok = false;
        public Error error;
        
        public ValidationResult message(String message) {
            if(error != null) {
                error.message = message;
            }
            return this;
        }
        
        public ValidationResult key(String key) {
            if(error != null) {
                error.key = key;
            }
            return this;
        }
        
    }
    
    public static ValidationResult required(String key, Object o) {
        RequiredCheck check = new RequiredCheck();
        return applyCheck(check, key, o);
    }

    public ValidationResult required(Object o) {
        String key = LocalVariablesNamesTracer.getAllLocalVariableNames(o).get(0);
        return Validation.required(key, o);
    }
    
    public static ValidationResult min(String key, Object o, double min) {
        MinCheck check = new MinCheck();
        check.min = min;
        return applyCheck(check, key, o);
    }

    public ValidationResult min(Object o, double min) {
        String key = LocalVariablesNamesTracer.getAllLocalVariableNames(o).get(0);
        return Validation.min(key, o, min);
    }
    
    public static ValidationResult email(String key, Object o) {
        EmailCheck check = new EmailCheck();
        return applyCheck(check, key, o);
    }

    public ValidationResult email(Object o) {
        String key = LocalVariablesNamesTracer.getAllLocalVariableNames(o).get(0);
        return Validation.email(key, o);
    }
    
    public static ValidationResult isTrue(String key, Object o) {
        IsTrueCheck check = new IsTrueCheck();
        return applyCheck(check, key, o);
    }

    public ValidationResult isTrue(Object o) {
        String key = LocalVariablesNamesTracer.getAllLocalVariableNames(o).get(0);
        return Validation.isTrue(key, o);
    }
    
    
    public static ValidationResult equals(String key, Object o, String otherName, Object to) {
        EqualsCheck check = new EqualsCheck();
        check.otherKey = otherName;
        check.otherValue = to;
        return applyCheck(check, key, o);
    }

    public ValidationResult equals(Object o, Object to) {
        String key = LocalVariablesNamesTracer.getAllLocalVariableNames(o).get(0);
        String otherKey = LocalVariablesNamesTracer.getAllLocalVariableNames(to).get(0);
        return Validation.equals(key, o, otherKey, to);
    }
    
    public static ValidationResult range(String key, Object o, double min, double max) {
        RangeCheck check = new RangeCheck();
        check.min = min;
        check.max = max;
        return applyCheck(check, key, o);
    }

    public ValidationResult range(Object o, double min, double max) {
        String key = LocalVariablesNamesTracer.getAllLocalVariableNames(o).get(0);
        return Validation.range(key, o, min, max);
    }
    
    public static ValidationResult minSize(String key, Object o, int minSize) {
        MinSizeCheck check = new MinSizeCheck();
        check.minSize = minSize;
        return applyCheck(check, key, o);
    }

    public ValidationResult minSize(Object o, int minSize) {
        String key = LocalVariablesNamesTracer.getAllLocalVariableNames(o).get(0);
        return Validation.minSize(key, o, minSize);
    }

    public static ValidationResult valid(String key, Object o) {
        ValidCheck check = new ValidCheck();
        check.key = key;
        return applyCheck(check, key, o);
    }

    public ValidationResult valid(Object o) {
        String key = LocalVariablesNamesTracer.getAllLocalVariableNames(o).get(0);
        return Validation.valid(key, o);
    }

    static ValidationResult applyCheck(AbstractAnnotationCheck check, String key, Object o) {
        try {
            ValidationResult result = new ValidationResult();
            if (!check.isSatisfied(o, o, null, null)) {
                Error error = new Error(key, check.getClass().getDeclaredField("mes").get(null) + "", check.getMessageVariables() == null ? new String[0] : check.getMessageVariables().values().toArray(new String[0]));
                Validation.current().errors.add(error);
                result.error = error;
                result.ok = false;
            } else {
                result.ok = true;
            }
            return result;
        } catch (Exception e) {
            throw new UnexpectedException(e);
        }
    }
}