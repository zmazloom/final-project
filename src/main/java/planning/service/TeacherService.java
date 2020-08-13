package planning.service;

import lombok.AllArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import planning.exception.InternalServerException;
import planning.exception.InvalidRequestException;
import planning.message.CommonMessage;
import planning.message.TeacherMessage;
import planning.model.*;
import planning.modelVO.TeacherAddVO;
import planning.modelVO.TeacherTimeVO;
import planning.modelVO.TeacherVO;
import planning.repository.TeacherCRUD;
import planning.repository.TeacherTimeCRUD;
import planning.utils.PasswordUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;

@Service
@AllArgsConstructor
public class TeacherService {

    private final TeacherCRUD teacherCRUD;
    private final TeacherTimeCRUD teacherTimeCRUD;

    private ModelMapper modelMapper = new ModelMapper();

    public TeacherVO getTeacherVO(Teacher teacher) {
        if (teacher == null)
            return null;

        return modelMapper.map(teacher, TeacherVO.class);
    }

    public List<TeacherVO> getTeacherVOs(List<Teacher> teachers) {
        List<TeacherVO> vos = new ArrayList<>();
        teachers.stream().filter(Objects::nonNull).forEach(teacher -> vos.add(getTeacherVO(teacher)));

        return vos;
    }

    public TeacherTimeVO getTeacherTimeVO(Plan plan, List<TeacherTime> teacherTimes) {
        if (teacherTimes == null)
            return null;

        List<Time> times = new ArrayList<>();

        for(TeacherTime teacherTime : teacherTimes) {
            times.add(teacherTime.getTime());
        }

        TeacherTimeVO teacherTimeVO = new TeacherTimeVO();
        teacherTimeVO.setPlanId(plan.getId());
        teacherTimeVO.setTimes(times);

        return teacherTimeVO;
    }

    public void deleteTeacher(Teacher teacher) {
        if (teacher != null) {
            teacher.setRemoved(true);
            teacherCRUD.saveAndFlush(teacher);
        }
    }

    public Teacher addTeacher(TeacherAddVO teacherAddVO, MultipartFile avatar) {
        validateTeacherInsert(teacherAddVO);

        Teacher teacher = new Teacher();

        if (avatar != null && !avatar.isEmpty()) {
            teacher.setAvatar(saveAvatarFile(avatar));
        }

        if (teacherAddVO.getPassword() == null || teacherAddVO.getPassword().equals(""))
            teacher.setPassword(PasswordUtils.generatePassword());
        else
            teacher.setPassword(teacherAddVO.getPassword());

        teacher.setFirstName(teacherAddVO.getFirstName());
        teacher.setLastName(teacherAddVO.getLastName());
        teacher.setUsername(teacherAddVO.getUsername());
        teacher.setPrefix(teacherAddVO.getPrefix());

        return teacherCRUD.saveAndFlush(teacher);
    }

    public Teacher updateTeacher(Teacher teacher, TeacherAddVO teacherAddVO, MultipartFile avatar) {
        validateTeacherUpdate(teacherAddVO);

        if (avatar != null && !avatar.isEmpty()) {
            if (teacher.getAvatar() != null && !teacher.getAvatar().equals(""))
                deleteOldAvatar(teacher.getAvatar());

            teacher.setAvatar(saveAvatarFile(avatar));
        }
        if (teacherAddVO.getPassword() != null)
            teacher.setPassword(teacherAddVO.getPassword());
        if (teacherAddVO.getFirstName() != null)
            teacher.setFirstName(teacherAddVO.getFirstName());
        if (teacherAddVO.getLastName() != null)
            teacher.setLastName(teacherAddVO.getLastName());
        if (teacherAddVO.getUsername() != null)
            teacher.setUsername(teacherAddVO.getUsername());
        if (teacherAddVO.getPrefix() != null)
            teacher.setPrefix(teacherAddVO.getPrefix());

        return teacherCRUD.saveAndFlush(teacher);
    }

    private String saveAvatarFile(MultipartFile avatar) {
        try {
            Calendar calendar = Calendar.getInstance();

            byte[] bytes = avatar.getBytes();
            Path path = Paths.get("./avatar/" + calendar.get(Calendar.YEAR) + calendar.get(Calendar.MONTH) + calendar.get(Calendar.DAY_OF_MONTH) + calendar.get(Calendar.HOUR) + calendar.get(Calendar.MINUTE) + calendar.get(Calendar.SECOND) + "-" + avatar.getOriginalFilename());
            Files.write(path, bytes);
            return path.toString();
        } catch (IOException ex) {
            throw InternalServerException.getInstance(TeacherMessage.getErrorSaveAvatar());
        }
    }

    private void deleteOldAvatar(String filePath) {
        File file = new File(filePath);
        if (file.exists())
            file.delete();
    }

    private void validateTeacherInsert(TeacherAddVO teacherAddVO) {
        if (teacherAddVO.getUsername() == null || teacherAddVO.getUsername().equals("") || teacherAddVO.getUsername().length() < 4)
            throw InvalidRequestException.getInstance(TeacherMessage.getInvalidUsername());

        if (teacherAddVO.getFirstName() == null || teacherAddVO.getFirstName().equals(""))
            throw InvalidRequestException.getInstance(CommonMessage.getParamRequired("firstName"));

        if (teacherAddVO.getLastName() == null || teacherAddVO.getLastName().equals(""))
            throw InvalidRequestException.getInstance(CommonMessage.getParamRequired("lastName"));
    }

    private void validateTeacherUpdate(TeacherAddVO teacherAddVO) {
        if (teacherAddVO.getUsername() != null && (teacherAddVO.getUsername().equals("") || teacherAddVO.getUsername().length() < 4))
            throw InvalidRequestException.getInstance(TeacherMessage.getInvalidUsername());

        if (teacherAddVO.getPassword() != null && teacherAddVO.getPassword().equals(""))
            throw InvalidRequestException.getInstance(CommonMessage.getParamRequired("password"));

        if (teacherAddVO.getFirstName() != null && teacherAddVO.getFirstName().equals(""))
            throw InvalidRequestException.getInstance(CommonMessage.getParamRequired("firstName"));

        if (teacherAddVO.getLastName() != null && teacherAddVO.getLastName().equals(""))
            throw InvalidRequestException.getInstance(CommonMessage.getParamRequired("lastName"));
    }

    public void addTeacherTimes(Teacher teacher, Plan plan, List<Time> times) {
        validateTeacherTimes(plan.getTimeType(), times);

        List<TeacherTime> teacherTimes = teacherTimeCRUD.getTeacherTimes(plan, teacher);

        if (times != null) {
            if (teacherTimes != null) {
                for (int i = 0; i < teacherTimes.size(); i++) {
                    boolean fined = false;
                    for (Time time : times) {
                        if (teacherTimes.get(i).getTime().equals(time)) {
                            times.remove(time);
                            fined = true;
                            break;
                        }
                    }
                    if (!fined) {
                        TeacherTime removedTeacherTime = teacherTimes.get(i);
                        teacherTimes.remove(removedTeacherTime);
                        teacherTimeCRUD.delete(removedTeacherTime);
                        i--;
                    }
                }
            }

            for (Time time : times) {
                TeacherTime teacherTime = new TeacherTime();
                teacherTime.setPlan(plan);
                teacherTime.setTeacher(teacher);
                teacherTime.setTime(time);

                teacherTimeCRUD.saveAndFlush(teacherTime);
            }
        }
    }

    private void validateTeacherTimes(TimeType planTypeTime, List<Time> times) {
        if(times != null && !times.isEmpty()) {
            if (planTypeTime.equals(TimeType.TWO_HOURS)) {

                List<Time> twoHourTimes = getTwoHourTimes();

                for(Time time : times) {
                    if(!twoHourTimes.contains(time)) {
                        throw InvalidRequestException.getInstance(TeacherMessage.getInvalidTwoTime());
                    }
                }
            }
            else {
                List<Time> oneThirtyHourTimes = getOneThirtyHourTimes();

                for(Time time : times) {
                    if(!oneThirtyHourTimes.contains(time)) {
                        throw InvalidRequestException.getInstance(TeacherMessage.getInvalidOneThirtyTime());
                    }
                }
            }
        }
    }

    static List<Time> getTwoHourTimes() {
        List<Time> twoHourList = new ArrayList<>();
        twoHourList.add(Time.SHANBE8T);
        twoHourList.add(Time.SHANBE10T);
        twoHourList.add(Time.SHANBE12T);
        twoHourList.add(Time.SHANBE14T);
        twoHourList.add(Time.SHANBE16T);
        twoHourList.add(Time.SHANBE18T);
        twoHourList.add(Time.YEKSHANBE8T);
        twoHourList.add(Time.YEKSHANBE10T);
        twoHourList.add(Time.YEKSHANBE12T);
        twoHourList.add(Time.YEKSHANBE14T);
        twoHourList.add(Time.YEKSHANBE16T);
        twoHourList.add(Time.YEKSHANBE18T);
        twoHourList.add(Time.DOSHANBE8T);
        twoHourList.add(Time.DOSHANBE10T);
        twoHourList.add(Time.DOSHANBE12T);
        twoHourList.add(Time.DOSHANBE14T);
        twoHourList.add(Time.DOSHANBE16T);
        twoHourList.add(Time.DOSHANBE18T);
        twoHourList.add(Time.SESHANBE8T);
        twoHourList.add(Time.SESHANBE10T);
        twoHourList.add(Time.SESHANBE12T);
        twoHourList.add(Time.SESHANBE14T);
        twoHourList.add(Time.SESHANBE16T);
        twoHourList.add(Time.SESHANBE18T);
        twoHourList.add(Time.CHARSHANBE8T);
        twoHourList.add(Time.CHARSHANBE10T);
        twoHourList.add(Time.CHARSHANBE12T);
        twoHourList.add(Time.CHARSHANBE14T);
        twoHourList.add(Time.CHARSHANBE16T);
        twoHourList.add(Time.CHARSHANBE18T);

        return twoHourList;
    }

    static List<Time> getOneThirtyHourTimes() {
        List<Time> oneThirtyHourList = new ArrayList<>();
        oneThirtyHourList.add(Time.SHANBE730O);
        oneThirtyHourList.add(Time.SHANBE9O);
        oneThirtyHourList.add(Time.SHANBE1030O);
        oneThirtyHourList.add(Time.SHANBE12O);
        oneThirtyHourList.add(Time.SHANBE1330O);
        oneThirtyHourList.add(Time.SHANBE15O);
        oneThirtyHourList.add(Time.SHANBE1630O);
        oneThirtyHourList.add(Time.SHANBE18O);
        oneThirtyHourList.add(Time.YEKSHANBE730O);
        oneThirtyHourList.add(Time.YEKSHANBE9O);
        oneThirtyHourList.add(Time.YEKSHANBE1030O);
        oneThirtyHourList.add(Time.YEKSHANBE12O);
        oneThirtyHourList.add(Time.YEKSHANBE1330O);
        oneThirtyHourList.add(Time.YEKSHANBE15O);
        oneThirtyHourList.add(Time.YEKSHANBE1630O);
        oneThirtyHourList.add(Time.YEKSHANBE18O);
        oneThirtyHourList.add(Time.DOSHANBE730O);
        oneThirtyHourList.add(Time.DOSHANBE9O);
        oneThirtyHourList.add(Time.DOSHANBE1030O);
        oneThirtyHourList.add(Time.DOSHANBE12O);
        oneThirtyHourList.add(Time.DOSHANBE1330O);
        oneThirtyHourList.add(Time.DOSHANBE15O);
        oneThirtyHourList.add(Time.DOSHANBE1630O);
        oneThirtyHourList.add(Time.DOSHANBE18O);
        oneThirtyHourList.add(Time.SESHANBE730O);
        oneThirtyHourList.add(Time.SESHANBE9O);
        oneThirtyHourList.add(Time.SESHANBE1030O);
        oneThirtyHourList.add(Time.SESHANBE12O);
        oneThirtyHourList.add(Time.SESHANBE1330O);
        oneThirtyHourList.add(Time.SESHANBE15O);
        oneThirtyHourList.add(Time.SESHANBE1630O);
        oneThirtyHourList.add(Time.SESHANBE18O);
        oneThirtyHourList.add(Time.CHARSHANBE730O);
        oneThirtyHourList.add(Time.CHARSHANBE9O);
        oneThirtyHourList.add(Time.CHARSHANBE1030O);
        oneThirtyHourList.add(Time.CHARSHANBE12O);
        oneThirtyHourList.add(Time.CHARSHANBE1330O);
        oneThirtyHourList.add(Time.CHARSHANBE15O);
        oneThirtyHourList.add(Time.CHARSHANBE1630O);
        oneThirtyHourList.add(Time.CHARSHANBE18O);

        return oneThirtyHourList;
    }

}
