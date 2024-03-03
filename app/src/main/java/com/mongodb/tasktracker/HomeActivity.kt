package com.mongodb.tasktracker

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import com.mongodb.tasktracker.databinding.ActivityHomeBinding
import com.mongodb.tasktracker.model.CourseInfo
import com.mongodb.tasktracker.model.SlotInfo
import io.realm.Realm
import com.mongodb.tasktracker.model.User
import io.realm.*
import io.realm.kotlin.where
import io.realm.mongodb.App
import io.realm.mongodb.AppConfiguration
import io.realm.mongodb.sync.SyncConfiguration
import org.bson.Document
import org.bson.types.ObjectId

class HomeActivity : AppCompatActivity() {
    lateinit var binding: ActivityHomeBinding
    lateinit var app: App
    private var studentName: String? = null
    private var studentEmail: String? = null
    private var departmentName: String? = null

    private var coursesInfo: List<CourseInfo>? = null
    private var slotsData: List<SlotInfo>?= null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Khởi tạo Realm
        Realm.init(this)
        val appConfiguration = AppConfiguration.Builder("finalproject-rujev").build()
        app = App(appConfiguration)  // Khởi tạo app

        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Nhận email từ Intent
        val userEmail = intent.getStringExtra("USER_EMAIL")
        if (userEmail != null) {
            fetchStudentData(userEmail)
        }

        binding.bottomNavigationView.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.userInterface -> replaceFragment(InterfaceFragment())
                R.id.user -> replaceFragment(InforFragment())
                R.id.gear -> replaceFragment(GearFragment())
                R.id.shop -> replaceFragment(ShopFragment())
                else -> { }
            }
            true
        }

        val showInforFragment = intent.getBooleanExtra("SHOW_INFOR_FRAGMENT", false)
        if (showInforFragment) {
            // Hiển thị InforFragment
            replaceFragment(InforFragment())
        } else {
            // Hiển thị InterfaceFragment làm màn hình chính
            replaceFragment(InterfaceFragment())
        }
    }

    private fun fetchStudentData(userEmail: String) {
        val mongoClient = app.currentUser()!!.getMongoClient("mongodb-atlas")
        val database = mongoClient.getDatabase("finalProject")
        val studentsCollection = database.getCollection("Students")

        val query = Document("email", userEmail)
        studentsCollection.findOne(query).getAsync { task ->
            if (task.isSuccess) {
                val studentDocument = task.get()
                if (studentDocument != null) {
                    studentName = studentDocument.getString("name")
                    studentEmail = studentDocument.getString("email")
                    val departmentId = studentDocument.getObjectId("departmentId")
                    fetchDepartmentData(departmentId)

                    //lấy danh sách enrolledCourses
                    val enrolledCourses = studentDocument.getList("enrolledCourses", ObjectId::class.java)
                    fetchCoursesData(enrolledCourses)

                    // Gọi fetchSlotsData sau khi dữ liệu sinh viên được lấy thành công
                    fetchSlotsData()

                } else {
                    // Xử lý trường hợp không tìm thấy sinh viên
                    Log.e("HomeActivity", "Không tìm thấy sinh viên với email: $userEmail")
                }
            } else {
                // Xử lý lỗi
                Log.e("HomeActivity", "Lỗi khi truy vấn: ${task.error}")
            }
        }
    }

    private fun fetchDepartmentData(departmentId: ObjectId) {
        val mongoClient = app.currentUser()!!.getMongoClient("mongodb-atlas")
        val database = mongoClient.getDatabase("finalProject")
        val departmentsCollection = database.getCollection("Departments")

        val query = Document("_id", departmentId)
        departmentsCollection.findOne(query).getAsync { task ->
            if (task.isSuccess) {
                val departmentDocument = task.get()
                if (departmentDocument != null) {
                    departmentName = departmentDocument.getString("name")
                } else {
                    Log.e("HomeActivity", "Không tìm thấy phòng ban với ID: $departmentId")
                }
            } else {
                Log.e("HomeActivity", "Lỗi khi truy vấn phòng ban: ${task.error}")
            }
        }
    }

    private fun fetchCoursesData(courseIds: List<ObjectId>) {
        val mongoClient = app.currentUser()!!.getMongoClient("mongodb-atlas")
        val database = mongoClient.getDatabase("finalProject")
        val coursesCollection = database.getCollection("Courses")
        val departmentsCollection = database.getCollection("Departments")

        val coursesInfo = mutableListOf<CourseInfo>()

        courseIds.forEach { courseId ->
            val query = Document("_id", courseId)

            coursesCollection.findOne(query).getAsync { task ->
                if (task.isSuccess) {
                    val courseDocument = task.get()
                    if (courseDocument != null) {
                        val title = courseDocument.getString("title")
                        val description = courseDocument.getString("description")
                        val departmentId = courseDocument.getObjectId("departmentId")
                        val credits = courseDocument.getInteger("credits", 0)

                        // Fetch department name
                        val deptQuery = Document("_id", departmentId)
                        departmentsCollection.findOne(deptQuery).getAsync { deptTask ->
                            if (deptTask.isSuccess) {
                                val departmentDocument = deptTask.get()
                                val departmentName = departmentDocument?.getString("name") ?: "Unknown"

                                // Add CourseInfo with department name
                                coursesInfo.add(CourseInfo(title, description, departmentName, credits))
                                checkAndPassCourses(coursesInfo, courseIds.size)
                            } else {
                                Log.e("HomeActivity", "Error fetching department: ${deptTask.error}")
                            }
                        }
                    }
                } else {
                    Log.e("HomeActivity", "Error fetching course: ${task.error}")
                }
            }
        }
    }

    private fun checkAndPassCourses(coursesInfo: List<CourseInfo>, totalCourses: Int) {
        if (coursesInfo.size == totalCourses) {
            passCoursesToFragment(coursesInfo)
        }
    }


    private fun passCoursesToFragment(coursesInfo: List<CourseInfo>) {
        this.coursesInfo = coursesInfo
        // Cập nhật InforFragment với dữ liệu mới
        val inforFragment = supportFragmentManager.findFragmentByTag("InforFragment") as? InforFragment
        inforFragment?.let {
            it.arguments = Bundle().apply {
                putString("name", studentName ?: "N/A")
                putString("email", studentEmail ?: "N/A")
                putString("department", departmentName ?: "N/A")
                putSerializable("courses", ArrayList(coursesInfo))
            }
            replaceFragment(it)
        }
    }

    private fun fetchSlotsData() {
        val mongoClient = app.currentUser()!!.getMongoClient("mongodb-atlas")
        val database = mongoClient.getDatabase("finalProject")
        val slotsCollection = database.getCollection("Slots")

        slotsCollection.count().getAsync { countTask ->
            if (countTask.isSuccess) {
                val totalCount = countTask.get().toInt()
                val slotsData = mutableListOf<SlotInfo>()
                var processedCount = 0

                slotsCollection.find().iterator().getAsync { task ->
                    if (task.isSuccess) {
                        val results = task.get()
                        results.forEach { slotDocument ->
                            val courseId = slotDocument.getObjectId("courseId")

                            // Fetch course data for each slot
                            fetchCourseDataForSlot(courseId, slotsData) {
                                processedCount++
                                if (processedCount == totalCount) {
                                    // Gửi dữ liệu đến InterfaceFragment khi đã xử lý xong slot cuối cùng
                                    sendSlotsDataToInterfaceFragment(slotsData)
                                }
                            }
                        }
                    } else {
                        Log.e("HomeActivity", "Error fetching slots: ${task.error}")
                    }
                }
            } else {
                Log.e("HomeActivity", "Error counting slots: ${countTask.error}")
            }
        }
    }

    private fun fetchCourseDataForSlot(courseId: ObjectId, slotsData: MutableList<SlotInfo>, onComplete: () -> Unit) {
        val mongoClient = app.currentUser()!!.getMongoClient("mongodb-atlas")
        val coursesCollection = mongoClient.getDatabase("finalProject").getCollection("Courses")

        coursesCollection.findOne(Document("_id", courseId)).getAsync { courseTask ->
            if (courseTask.isSuccess) {
                val courseDocument = courseTask.get()
                val courseTitle = courseDocument.getString("title")

                val slotInfo = SlotInfo(
                    startTime = "", // Set appropriate value
                    endTime = "", // Set appropriate value
                    day = "", // Set appropriate value
                    courseId = courseId.toString(),
                    courseTitle = courseTitle
                )
                slotsData.add(slotInfo)

                onComplete() // Gọi hàm callback khi xử lý xong
            }
        }
    }


    private fun sendSlotsDataToInterfaceFragment(data: List<SlotInfo>) {
        slotsData = data
        val interfaceFragment = InterfaceFragment().apply {
            arguments = Bundle().apply {
                putSerializable("slotsData", ArrayList(data))
            }
        }
        replaceFragment(interfaceFragment)
    }


    private fun replaceFragment(fragment: Fragment) {
        // Kiểm tra và cập nhật dữ liệu cho InterfaceFragment hoặc InforFragment nếu cần
        if (fragment is InterfaceFragment && slotsData != null) {
            fragment.arguments = Bundle().apply {
                putSerializable("slotsData", ArrayList(slotsData))
            }
        } else if (fragment is InforFragment && coursesInfo != null) {
            fragment.arguments = Bundle().apply {
                putString("name", studentName ?: "N/A")
                putString("email", studentEmail ?: "N/A")
                putString("department", departmentName ?: "N/A")
                putSerializable("courses", ArrayList(coursesInfo))
            }
        }

        // Thực hiện thay thế Fragment
        supportFragmentManager.beginTransaction().apply {
            replace(R.id.frame_layout, fragment, fragment.javaClass.simpleName)
            commit()
        }
    }

}
